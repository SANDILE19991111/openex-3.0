# OpenEx 3.0 — Week 1 (Core Reactor + Vault)

Aligned to the official capstone brief. This covers all Week 1 deliverables:

- [x] Kotlin + Spring Boot mainframe (monorepo-style single service for now)
- [x] Postgres vault + Flyway migrations (`accounts`, `ledger_entries`, `users`, `orders`, `trades`)
- [x] Double-entry ledger — **CREDIT/DEBIT direction enum**, balance always derived, never stored
- [x] JWT authentication (`/api/auth/register`, `/api/auth/login`, Bearer token on everything else)
- [x] Idempotency-Key protection on `/api/wallets/deposit` and `POST /api/orders`
- [x] In-memory matching engine — LIMIT + MARKET orders, **partial fills**, price-time priority
- [x] Redis added to the stack (provisioned now; wire in caching/rate-limiting as you extend it)
- [x] GitHub Actions CI running Kotlin tests on every PR
- [ ] Conventional commits + PR workflow — **this part is on you**, see below

## Project layout
```
openex-3.0/
├── docker-compose.yml
├── .github/workflows/core-reactor-ci.yml
└── core-reactor/
    └── src/main/kotlin/com/openex/core/
        ├── account/     # Account entity (user_id, currency) + REST
        ├── ledger/      # LedgerEntry (CREDIT/DEBIT), LedgerService, idempotent deposit
        ├── matching/     # Order, Trade, OrderBook, MatchingEngine, REST
        ├── security/     # JwtService, JwtAuthFilter, SecurityConfig
        └── user/         # User entity, AuthController (register/login)
```

## Run it
```bash
docker compose up --build
```
Starts Postgres, Redis, and the API on `localhost:8080`. Flyway runs migrations
automatically on boot.

## 1. Register and get a JWT
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "sand", "password": "correcthorsebattery"}'
```
Copy the `token` from the response. Every other endpoint requires it:
```bash
export TOKEN="<paste token here>"
```

## 2. Create an account and deposit funds
```bash
curl -X POST http://localhost:8080/api/wallets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId": "<your-user-id-from-register>", "currency": "USD"}'
# -> copy the returned account "id"

curl -X POST http://localhost:8080/api/wallets/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: deposit-001" \
  -d '{"accountId": "<account-id>", "amount": 1000, "reference": "starting balance"}'
```
Replay the same `Idempotency-Key` and confirm the balance does NOT double.

## 3. Place orders and watch them match
```bash
# Resting sell order
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-sell-001" \
  -d '{"userId":"<user-id>","tradingPair":"BTC-USD","side":"SELL","orderType":"LIMIT","price":100.00,"quantity":5}'

# Incoming buy for MORE than the resting sell -> partial fill, remainder rests
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-buy-001" \
  -d '{"userId":"<user-id>","tradingPair":"BTC-USD","side":"BUY","orderType":"LIMIT","price":100.00,"quantity":8}'
# -> status: PARTIALLY_FILLED, filledQuantity: 5, tradesExecuted: 1

# Check the live order book
curl http://localhost:8080/api/orders/book/BTC-USD -H "Authorization: Bearer $TOKEN"
```

## Why the ledger is structured this way
`ledger_entries` has a `direction` column (`CREDIT`/`DEBIT`) with a strictly
positive `amount` — matching the brief's schema exactly, not a signed-amount
trick. `LedgerService.recordMovement` rejects any batch of entries where
total CREDIT ≠ total DEBIT. Balance is always `SUM` over entries, never a
mutable column, so nobody can silently edit history — only append to it.

## Why the matching engine is structured this way
One `OrderBook` per trading pair, held in memory, matched under a
per-book lock (`synchronized(book)`) so trades within a pair are strictly
serialized — no race between two incoming orders hitting the same book.
Trades always execute at the **resting** order's price. MARKET orders walk
the book until filled or liquidity runs out; they never rest. LIMIT orders
rest any unfilled remainder. See `MatchingEngineTest.kt` for the exact
partial-fill, price-time-priority, and cancel scenarios this satisfies.

## Git workflow you still need to follow yourself
```bash
git checkout -b feature/ledger-and-auth
# ...commit with conventional commits, e.g.:
git commit -m "feat(ledger): CREDIT/DEBIT double-entry with idempotent deposits"
git commit -m "feat(auth): JWT registration and login"
git commit -m "feat(engine): price-time priority matching with partial fills"
git push origin feature/ledger-and-auth
# Open a PR titled exactly: "feat(core): matching engine and ledger integration"
```
CI (`.github/workflows/core-reactor-ci.yml`) runs Kotlin unit tests against a
throwaway Postgres service on every PR touching `core-reactor/`. It must be
green before merging — no direct pushes to `main`.

## Next up (Week 2)
Spring WebSocket endpoint broadcasting trade events + React SPA with live
order book UI. Ask when you're ready to start that slice.
