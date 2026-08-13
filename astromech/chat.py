"""
Day 12: a LangChain-wrapped chat call to a locally-running Ollama model,
with a fixed financial-assistant persona.

No tool calling yet (that's Day 13, where this agent gets a wallet-balance
tool). Right now it just answers questions in character - no real data
lookups, no ability to reach the Kotlin backend.
"""
import os
from langchain_ollama import ChatOllama
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser

OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3")
OLLAMA_BASE_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")

SYSTEM_PROMPT = """You are the Astromech droid, OpenEx 3.0's onboard trading assistant.
You speak concisely and precisely, like a seasoned exchange operator - no fluff, no hype.
You do not give financial advice or price predictions.
You cannot place trades on the user's behalf; if asked to trade, explain that trades must be placed through the Trading page.
You do not yet have access to the user's real wallet balances or account data - if asked about those, say so honestly rather than guessing a number.
"""

_prompt = ChatPromptTemplate.from_messages([
    ("system", SYSTEM_PROMPT),
    ("human", "{input}"),
])


def run_chat(message: str) -> str:
    """Sends one message to the local Ollama model and returns its reply."""
    llm = ChatOllama(model=OLLAMA_MODEL, base_url=OLLAMA_BASE_URL, temperature=0.3)
    chain = _prompt | llm | StrOutputParser()
    reply = chain.invoke({"input": message})
    return reply.strip() or "I couldn't come up with a response for that."
