"""
Day 13: the agent now has a real tool - it can call the Kotlin backend's
GET /api/wallets to answer balance questions with actual numbers, instead
of admitting (as it did through Day 12) that it can't see account data.

Each chat request builds a short-lived agent bound to that request's JWT and
userId (via a closure on the tool function), since the token identifies who
"the user" is for that call. There's no shared/global agent instance - this
keeps the wallet lookup scoped to exactly what the calling user is
authorized to see, using their real token rather than a service-level
bypass.
"""
import os
import requests
from langchain_ollama import ChatOllama
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.tools import tool

OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3")
OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")
KOTLIN_API_BASE = os.environ.get("KOTLIN_API_BASE", "http://localhost:8080")

SYSTEM_PROMPT = """You are the Astromech droid, OpenEx 3.0's onboard trading assistant.
You speak concisely and precisely, like a seasoned exchange operator - no fluff, no hype.
You do not give financial advice or price predictions.
You cannot place trades on the user's behalf; if asked to trade, explain that trades must be placed through the Trading page.
You can look up the user's real wallet balances using the get_wallet_balances tool whenever they ask about their balance, holdings, or funds.
Never invent a balance - if the tool call fails or returns no wallets, say so plainly rather than guessing a number.
"""


def _make_wallet_tool(user_id: str, jwt_token: str):
    @tool
    def get_wallet_balances(currency: str = "") -> str:
        """Look up the current user's wallet balances from the exchange.
        Optionally pass a currency code (e.g. 'USD', 'BTC') to filter to one wallet.
        Returns each wallet's currency and balance, one per line."""
        try:
            resp = requests.get(
                f"{KOTLIN_API_BASE}/api/wallets",
                params={"userId": user_id},
                headers={"Authorization": f"Bearer {jwt_token}"},
                timeout=5,
            )
            resp.raise_for_status()
            wallets = resp.json()
        except requests.RequestException as exc:
            return f"Could not reach the wallet service: {exc}"

        if not wallets:
            return "This user has no wallets yet."

        if currency:
            wallets = [w for w in wallets if w["currency"].upper() == currency.upper()]
            if not wallets:
                return f"No wallet found for currency {currency}."

        return "\n".join(f"{w['currency']}: {w['balance']}" for w in wallets)

    return get_wallet_balances


def run_chat(message: str, user_id: str, jwt_token: str) -> str:
    """Runs one turn of the tool-calling agent and returns its text reply."""
    llm = ChatOllama(model=OLLAMA_MODEL, base_url=OLLAMA_BASE_URL, temperature=0.2)
    tools = [_make_wallet_tool(user_id, jwt_token)]

    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT),
        ("human", "{input}"),
        MessagesPlaceholder(variable_name="agent_scratchpad"),
    ])

    agent = create_tool_calling_agent(llm, tools, prompt)
    executor = AgentExecutor(agent=agent, tools=tools, verbose=False, max_iterations=4)

    result = executor.invoke({"input": message})
    return (result.get("output") or "").strip() or "I couldn't come up with a response for that."
