# OpenEx 3.0

A simulated crypto exchange built as a 3-week capstone: a Kotlin/Spring Boot
backend with a double-entry ledger and a real price-time-priority matching
engine, a React trading terminal with live charts and a WebSocket-driven
order book, and a Python/LangChain AI assistant backed by a local Ollama
model.

**Repo:** https://github.com/SANDILE19991111/openex-3.0

## What's actually implemented

### Backend (`core-reactor/` - Kotlin, Spring Boot)
- JWT authentication (register/login)
- Double-entry ledger - every balance change is a `CREDIT`/`DEBIT` pair;
  balances are always derived (`SUM` over ledger entries), never a mutable
  column
- In-memory matching engine - LIMIT, MARKET, and STOP orders, price-time
  priority, partial fills
- **Trade settlement** - when a trade executes, real funds move between the
  buyer's and seller's wallets via the ledger (not just a paper record)
- **Balance validation** - you can't sell an asset you don't hold or buy
  more than you can afford; wallets auto-create on first trade in a new
  currency
- Idempotency-Key protection on deposits and order creation
- Multi-coin markets - 10 trading pairs seeded with starting prices on boot
- Order history with cancellation
- Spring WebSocket (STOMP) - broadcasts live order book + trade updates
- Full test suite for the matching engine, trade settlement, and balance
  validation (`./gradlew test`)

### Frontend (`holonet-ui/` - React, Vite, TypeScript)
- Login/Register, Dashboard (wallets + deposit faucet), Markets (10-coin
  table with live prices), Trading (order forms + live order book),
  Order History (with cancel)
- Live line chart and real OHLC candlestick chart, with a timeframe
  selector (1m / 2h / 3h / 4h / daily / monthly / yearly)
- Your own open orders are drawn as price lines directly on the chart
- Floating AI chat widget

### AI microservice (`astromech/` - Python, Flask, LangChain, Ollama)
- Simulated market data (random walk with drift + moving averages),
  genuinely live-updating in memory
- Chat endpoint backed by a local Ollama model (`llama3.2:1b` by default -
  chosen for compatibility with modest hardware)
- Answers real wallet-balance questions by calling the Kotlin backend's
  `GET /api/wallets` with the user's own JWT - never invents a number

## Architecture
```
+-------------+      +-------------------+      +-------------+
|  React SPA   |----->|  Kotlin backend   |<---->|  Postgres    |
| (holonet-ui) | WS   |  (core-reactor)   |      |              |
+------+------+      +--------+----------+      +-------------+
       |                       ^
       | REST                  | REST (with the user's JWT)
       v                       |
+-------------+      +--------+----------+
|  Astromech   |----->|      Ollama       |
|  (Flask/AI)  |      |  (local LLM)      |
+-------------+      +-------------------+
```

## Running it locally (verified working setup)

You need 4 things running at once, each in its own terminal.

### 1. Postgres
Install natively (or via Docker if that's working on your machine), then:
```sql
CREATE USER openex WITH PASSWORD 'openex_dev_password';
CREATE DATABASE openex OWNER openex;
```

### 2. Kotlin backend
```powershell
cd core-reactor
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/openex"
$env:SPRING_DATASOURCE_USERNAME="openex"
$env:SPRING_DATASOURCE_PASSWORD="openex_dev_password"
$env:OPENEX_JWT_SECRET="dev-only-change-me-this-must-be-at-least-32-bytes-long"
.\gradlew.bat bootRun
```
Runs on `http://localhost:8080`. Flyway applies migrations automatically.

### 3. Python/AI microservice
Requires Ollama (https://ollama.com/download) installed, with a model pulled:
```powershell
ollama pull llama3.2:1b
```
Then:
```powershell
cd astromech
python -m venv venv
.\venv\Scripts\activate
pip install -r requirements.txt
$env:OLLAMA_MODEL="llama3.2:1b"
python app.py
```
Runs on `http://localhost:5001`.

### 4. Frontend
```powershell
cd holonet-ui
npm install
npm run dev
```
Runs on `http://localhost:5173`.

### Try it
1. Register, create a USD wallet, deposit funds
2. Go to Markets, pick a coin, go to Trading
3. Place a limit order - try opening two browser tabs as two different
   users and matching an order between them to see the live order book
   update in real time
4. Check Order History, cancel an open order
5. Try the AI chat widget - ask about your wallet balance

## Docker
A `docker-compose.yml` at the root boots the whole stack (Postgres, Redis,
backend, AI service, Ollama, frontend) with health checks in the right
order. In practice, running all of this simultaneously on modest hardware
(4-8GB RAM) can be resource-constrained - the native setup above is the
more reliably tested path.

## Deployment
`render.yaml` provisions a live deployment (Postgres + backend + AI service
+ frontend) on Render's free tier - see `DEPLOYMENT.md` for the exact
steps. The AI chat feature requires Ollama, which needs a paid Render tier
with real RAM to run reliably; the free-tier deploy omits it, and the chat
endpoint fails gracefully ("AI assistant unavailable") without affecting
anything else.

## Known limitations, honestly
- **AI chat is slow on constrained hardware.** A local LLM on a CPU-only,
  RAM-limited machine can take a while to respond. The chat logic uses a
  lightweight keyword-routing approach (check if a question is about
  wallet balance, fetch real data if so, then one model call to phrase the
  answer) rather than a full multi-step LangChain agent loop, specifically
  to cut latency - this is a deliberate simplification for usability on
  modest hardware.
- **Market-buy orders aren't balance-checked** - the fill price isn't known
  ahead of time, so this is left unvalidated for now (limit orders and all
  sells are validated).
- **Docker Compose works in principle** but wasn't the primary tested path
  given local hardware/networking constraints during development - the
  native run instructions above are the reliable path.
- **Free-tier Render deploys sleep after 15 minutes of inactivity** - the
  first request after idle time takes 30-60 seconds to wake up.

## Tech stack
| Layer | Tech |
|---|---|
| Backend | Kotlin, Spring Boot, Spring Security, Spring WebSocket, JPA/Hibernate, Flyway |
| Database | PostgreSQL |
| Frontend | React, TypeScript, Vite, Zustand, Chart.js, STOMP/SockJS |
| AI/Analytics | Python, Flask, LangChain, Ollama, Pandas, NumPy |
| Testing | JUnit 5, Mockito-Kotlin |
| CI | GitHub Actions |
