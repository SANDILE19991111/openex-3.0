# Astromech — Days 11-13: Python Microservice

Flask microservice providing:
1. **Simulated market data** (Day 11) - random walk with drift + moving averages
2. **Chat with a local LLM** (Day 12) - `POST /api/chat` via LangChain + Ollama
3. **Agentic tool calling** (Day 13) - the agent can now call the Kotlin
   backend's `GET /api/wallets` to answer balance questions with real numbers

## Setup

### 1. Install Ollama + pull a model
```powershell
ollama pull llama3
```
If your machine is RAM-constrained, use a smaller model instead - still
demonstrates the same LangChain/Ollama tool-calling integration:
```powershell
ollama pull llama3.2:1b
```

### 2. Python dependencies
```powershell
cd astromech
python -m venv venv
.\venv\Scripts\activate
pip install -r requirements.txt
```

### 3. Run it
```powershell
$env:OLLAMA_MODEL="llama3.2:1b"
$env:KOTLIN_API_BASE="http://localhost:8080"
python app.py
```
Runs on `http://localhost:5001`. The Kotlin backend must already be running
(see the root README) since the wallet tool calls it directly.

## Endpoints
| Route | Method | Purpose |
|---|---|---|
| `/health` | GET | Health check |
| `/api/market-data/pairs` | GET | List supported trading pairs |
| `/api/market-data/{pair}` | GET | Simulated price history + SMA |
| `/api/chat` | POST | Agentic chat - needs `Authorization: Bearer <jwt>` header + `{"message", "userId"}` body |

## Try the wallet tool for real
You need a real JWT and userId from the Kotlin backend first - register/login
via `POST /api/auth/register` (see root README), then:
```powershell
$env:TOKEN = "<paste the JWT here>"
$env:USERID = "<paste the userId here>"
$body = "{`"message`": `"What's my wallet balance?`", `"userId`": `"$env:USERID`"}"
$body | Out-File -Encoding utf8 -NoNewline chat_wallet_test.json
curl.exe -X POST http://localhost:5001/api/chat -H "Content-Type: application/json" -H "Authorization: Bearer $env:TOKEN" -d "@chat_wallet_test.json"
```
If you've deposited funds into a wallet already, the reply should quote your
actual balance - not a made-up number. If the tool call fails (wrong/expired
token, Kotlin backend not running), the agent should say so honestly rather
than guessing.

## Why the JWT gets forwarded, not re-derived
`GET /api/wallets` on the Kotlin side requires authentication. Flask forwards
the *same* JWT the frontend already sends for chat, into the LangChain tool's
HTTP call to Kotlin. This is what makes "securely reading wallet balances"
actually secure - the agent only ever sees what the calling user is already
authorized to see, using their real token rather than a service-level bypass
or a hardcoded admin credential.

## Next up
Day 14: Chart.js on the frontend rendering this service's market data, plus
a floating chat widget wired to `/api/chat`.
