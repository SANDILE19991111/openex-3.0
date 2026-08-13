# Astromech — Day 11 & 12: Python Microservice

Flask microservice providing:
1. **Simulated market data** (Day 11) — random walk with drift + moving averages
2. **Chat with a local LLM** (Day 12) — `POST /api/chat`, a financial-persona
   assistant backed by Ollama, via LangChain

Day 13 (tool calling into the Kotlin wallet API) isn't wired in yet — this
is scoped to just Day 12's deliverable so it lands as its own commit.

## Setup

### 1. Install Ollama
Download from **https://ollama.com/download** — native Windows installer,
no WSL required.

### 2. Pull a model
```powershell
ollama pull llama3
```
This downloads several GB the first time. Once done, Ollama runs as a
background service on `http://localhost:11434` automatically — you don't
need to manually start it each time.

### 3. Python dependencies
```powershell
cd astromech
python -m venv venv
.\venv\Scripts\activate
pip install -r requirements.txt
```

### 4. Run it
```powershell
python app.py
```
Runs on `http://localhost:5001`.

## Endpoints
| Route | Method | Purpose |
|---|---|---|
| `/health` | GET | Health check |
| `/api/market-data/pairs` | GET | List supported trading pairs |
| `/api/market-data/{pair}` | GET | Simulated price history + SMA |
| `/api/chat` | POST | Chat with the Astromech persona — `{"message": "..."}` |

## Try the chat endpoint
```powershell
$body = '{"message": "What can you help me with?"}'
curl.exe -X POST http://localhost:5001/api/chat -H "Content-Type: application/json" -d $body
```
Should return `{"reply": "..."}` with the model's response in character —
concise, no financial advice, and honest that it can't yet see real wallet
data (that's Day 13).

## If it fails with a 503
That means Flask couldn't reach Ollama. Check:
```powershell
ollama list
```
If `llama3` isn't listed, `ollama pull llama3` didn't finish. If it is
listed but the chat call still fails, confirm Ollama's actually running:
```powershell
curl.exe http://localhost:11434
```
Should return `Ollama is running`.

## Next up
Day 13: give the agent a tool that calls the Kotlin backend's
`GET /api/wallets` (with the caller's real JWT) so it can answer balance
questions with real numbers instead of admitting it can't.
