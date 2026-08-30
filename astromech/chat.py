"""
Speed-optimized version: the full LangChain tool-calling AgentExecutor was
doing 1-2 model round trips just to decide whether to call a tool, then
another to phrase the final answer - each round trip is slow on
constrained, CPU-only hardware. This version routes by keyword instead:
if the question looks like a balance question, call the wallet tool
directly (no LLM needed to "decide" that), then make exactly ONE model
call to phrase the final answer using the real data. Non-balance questions
also get exactly one model call, with no tool overhead at all.

Net effect: worst case is 1 model generation instead of up to 2-4. This is
a deliberate simplification versus a "real" agent - it can't chain
multiple tools or reason about ambiguous requests, but for a single
wallet-balance tool this covers the actual use case at a fraction of the
latency on weak hardware.
"""
import os
import requests
from langchain_ollama import ChatOllama
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser

OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3")
OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")
KOTLIN_API_BASE = os.environ.get("KOTLIN_API_BASE", "http://localhost:8080")

BALANCE_KEYWORDS = ("balance", "wallet", "fund", "holding", "how much", "own", "have")

SYSTEM_PROMPT = """You are the Astromech droid, OpenEx 3.0's onboard trading assistant.
You speak concisely and precisely, like a seasoned exchange operator - no fluff, no hype.
You do not give financial advice or price predictions.
You cannot place trades on the user's behalf; if asked to trade, explain that trades must be placed through the Trading page.
Keep answers short - two or three sentences at most.
"""

BALANCE_PROMPT = """You are the Astromech droid, OpenEx 3.0's onboard trading assistant.
The user asked a question about their wallet balance. Here is their real, current wallet data:

{wallet_data}

Answer their question using ONLY this real data - never invent a number. Keep it to one or two short sentences.
User's question: {question}
"""

_llm = None


def _get_llm():
    global _llm
    if _llm is None:
        _llm = ChatOllama(
            model=OLLAMA_MODEL,
            base_url=OLLAMA_BASE_URL,
            temperature=0.2,
            num_predict=120,
            num_ctx=1024,
        )
    return _llm


def _fetch_wallet_data(user_id: str, jwt_token: str) -> str:
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
        return f"(wallet service unreachable: {exc})"

    if not wallets:
        return "The user has no wallets yet."

    return "\n".join(f"{w['currency']}: {w['balance']}" for w in wallets)


def run_chat(message: str, user_id: str, jwt_token: str) -> str:
    """Answers one message with exactly one model call."""
    llm = _get_llm()
    is_balance_question = any(kw in message.lower() for kw in BALANCE_KEYWORDS)

    if is_balance_question:
        wallet_data = _fetch_wallet_data(user_id, jwt_token)
        prompt = ChatPromptTemplate.from_template(BALANCE_PROMPT)
        chain = prompt | llm | StrOutputParser()
        reply = chain.invoke({"wallet_data": wallet_data, "question": message})
    else:
        prompt = ChatPromptTemplate.from_messages([
            ("system", SYSTEM_PROMPT),
            ("human", "{input}"),
        ])
        chain = prompt | llm | StrOutputParser()
        reply = chain.invoke({"input": message})

    return reply.strip() or "I couldn't come up with a response for that."
