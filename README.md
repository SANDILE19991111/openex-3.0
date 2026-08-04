# OpenEx 3.0

Simulated crypto exchange. Kotlin/Spring Boot backend with a double-entry
ledger and an in-memory price-time-priority matching engine, plus a React
trading terminal with a live, WebSocket-driven order book.

## Week 1 — Core Engine & DB Integrity (`core-reactor/`)
- [x] Kotlin + Spring Boot backend, Postgres + Flyway migrations
- [x] Double-entry ledger — CREDIT/DEBIT, balance always derived, never stored
- [x] JWT authentication (`/api/auth/register`, `/api/auth/login`)
- [x] Idempotency-Key protection on deposits and order creation
- [x] In-memory matching engine — LIMIT + MARKET, partial fills, price-time priority
- [x] GitHub Actions CI running Kotlin tests on every PR

See `core-reactor`'s section below for the full route table and how to run it.

## Week 2 — Real-Time Streaming & UI (`holonet-ui/`)
- [x] Spring WebSocket (STOMP) endpoint broadcasting order book + trade events
- [x] React + Vite SPA with routing (Login / Dashboard / Trading)
- [x] Auth UI storing the JWT, wallet dashboard with balances + deposit faucet
- [x] Order forms (limit/market, buy/sell) posting to the REST API with a
      fresh Idempotency-Key per submission
- [x] Live order book — updates via WebSocket the instant any order changes
      the book, no page refresh

See `holonet-ui/README.md` for frontend-specific setup.

## Running everything locally (no Docker)

### 1. Backend
```bash
cd core-reactor
# Postgres must already be running locally with an `openex` db/user — see below
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/openex"
$env:SPRING_DATASOURCE_USERNAME="openex"
$env:SPRING_DATASOURCE_PASSWORD="openex_dev_password"
$env:OPENEX_JWT_SECRET="dev-only-change-me-this-must-be-at-least-32-bytes-long"
.\gradlew.bat bootRun
```
Runs on `http://localhost:8080`. Flyway applies migrations automatically.

### 2. Frontend (separate terminal)
```bash
cd holonet-ui
npm install
npm run dev
```
Runs on `http://localhost:5173`, proxies `/api` and `/ws` to the backend.

### 3. Open the app
Go to `http://localhost:5173`, register a user, create a wallet, deposit
funds, then head to the Trading page and place an order. Open a second
browser tab (or an incognito window, logged in as a different user) and place
an opposing order — watch both tabs' order books update live via WebSocket.

## Postgres setup (one-time, if not already done)
```sql
CREATE USER openex WITH PASSWORD 'openex_dev_password';
CREATE DATABASE openex OWNER openex;
```

## Backend route table
| Route | Method | Purpose |
|---|---|---|
| `/api/auth/register` | POST | Register, get JWT |
| `/api/auth/login` | POST | Login, get JWT |
| `/api/wallets` | POST | Create a wallet/account |
| `/api/wallets/{id}` | GET | Wallet details + live balance |
| `/api/wallets?userId=` | GET | List a user's wallets |
| `/api/wallets/deposit` | POST | Faucet — idempotency-key protected |
| `/api/orders` | POST | Place order — idempotency-key protected |
| `/api/orders/{id}` | GET | Order status |
| `/api/orders/{id}` | DELETE | Cancel order |
| `/api/orders/book/{tradingPair}` | GET | Live order book snapshot (REST) |
| `/ws` | WS (STOMP/SockJS) | Subscribe to `/topic/orderbook/{pair}`, `/topic/trades/{pair}` |

## Why the ledger is structured this way
`ledger_entries` has a `direction` column (`CREDIT`/`DEBIT`) with a strictly
positive `amount`. `LedgerService.recordMovement` rejects any batch of
entries where total CREDIT ≠ total DEBIT. Balance is always `SUM` over
entries, never a mutable column.

## Why the matching engine is structured this way
One `OrderBook` per trading pair, held in memory, matched under a per-book
lock so trades within a pair are strictly serialized. Trades always execute
at the **resting** order's price. MARKET orders walk the book until filled or
liquidity runs out; they never rest. LIMIT orders rest any unfilled
remainder. See `MatchingEngineTest.kt` for partial-fill, price-time-priority,
and cancel scenarios.

## Why the WebSocket layer is structured this way
`OrderController` broadcasts a fresh order book snapshot (and any executed
trades) via `OrderBookBroadcaster` right after `MatchingEngine.submit()`
returns — kept in the controller layer rather than inside `MatchingEngine`
itself, so the matching engine stays a pure, easily-unit-tested component
with no messaging/Spring dependency baked in.

## Git workflow
```bash
git checkout -b feature/websockets-and-ui
git commit -m "feat(ui): real-time order book integration"
git push origin feature/websockets-and-ui
# Open a PR titled exactly: "feat(ui): real-time order book integration"
```

## Next up (Week 3)
Python/Flask market data simulator, Ollama + LangChain agentic assistant
that can answer balance questions by calling `GET /api/wallets`.
