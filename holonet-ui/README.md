# Holonet UI — Week 2 Frontend

React + Vite trading terminal. Talks to the Kotlin backend over REST for
actions (login, wallets, orders) and over STOMP/WebSocket for live order book
updates — no page refresh needed to see a trade.

## Setup
```bash
cd holonet-ui
npm install
npm run dev
```
Opens on `http://localhost:5173`. The Vite dev server proxies `/api` and `/ws`
to `http://localhost:8080`, so make sure the backend (`core-reactor`) is
already running first — see the root README for that.

## What's here
```
src/
├── api/
│   ├── client.ts    # REST calls (auth, wallets, orders) — attaches JWT
│   └── ws.ts        # STOMP/SockJS client — live order book + trade feed
├── store/
│   └── authStore.ts # Zustand store: JWT + user, persisted to localStorage
├── components/
│   └── OrderBook.tsx
├── pages/
│   ├── LoginPage.tsx      # register/login toggle
│   ├── DashboardPage.tsx  # wallet balances + create wallet + deposit faucet
│   └── TradingPage.tsx    # order form (limit/market, buy/sell) + live book
├── App.tsx           # routing + navbar + auth guard
└── main.tsx
```

## How the live order book works
1. `TradingPage` fetches an initial snapshot via `GET /api/orders/book/{pair}` on mount.
2. It then subscribes to `/topic/orderbook/{pair}` over STOMP (`subscribeOrderBook` in `ws.ts`).
3. Every time *any* order is placed (by any user, in any browser tab), the
   backend pushes a fresh snapshot to that topic, and every subscriber's
   order book updates instantly — no polling, no refresh.

## Trading pair
Hardcoded to `BTC-USD` for now (`TRADING_PAIR` constant in `TradingPage.tsx`).
Multi-pair selection is a natural next step but wasn't required for Week 2.

## Known simplification
The WebSocket handshake endpoint (`/ws`) is currently open (`permitAll()` in
the backend's `SecurityConfig`) rather than authenticated. For this bootcamp
project's scope that's acceptable — anyone can *watch* the public order book,
which is realistic (real exchanges show order books to logged-out visitors
too). Placing orders still requires a valid JWT via the REST API regardless.
