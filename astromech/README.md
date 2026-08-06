# Astromech — Day 11: Python Market Simulator

Flask microservice generating simulated market ticks — a random walk with
drift, plus short/long simple moving averages — for each of the 10 trading
pairs the exchange supports.

Ollama/LangChain agentic chat (Days 12–13) isn't wired in yet — this is
deliberately scoped to just Day 11's deliverable so it lands as its own
commit.

## Setup
```powershell
cd astromech
python -m venv venv
.\venv\Scripts\activate
pip install -r requirements.txt
python app.py
```
Runs on `http://localhost:5001`.

## Endpoints
| Route | Method | Purpose |
|---|---|---|
| `/health` | GET | Health check |
| `/api/market-data/pairs` | GET | List the 10 supported trading pairs |
| `/api/market-data/{pair}?points=200` | GET | Simulated price history + SMA for one pair |

## Try it
```powershell
curl.exe http://localhost:5001/api/market-data/pairs
curl.exe http://localhost:5001/api/market-data/BTC-USD
```
The second call returns `currentPrice`, `smaShort`, `smaLong`, and a `ticks`
array of `{timestamp, price, smaShort, smaLong}` — one per simulated minute,
200 by default.

## How the simulation works
Each trading pair gets a random walk seeded deterministically from its own
name (`market_data.py: _seed_for`), so repeated calls return the same
historical shape rather than jumping around on every refresh — useful once
this is wired into a chart. Starting prices mirror `SupportedPairs.kt` on
the Kotlin side so the numbers feel consistent across services.

## Next up
Day 12: install Ollama, wire in a LangChain agent behind `/api/chat`.
