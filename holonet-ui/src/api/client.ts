import { useAuthStore } from '../store/authStore'

// In local dev, these are relative paths proxied by Vite (see vite.config.ts).
// In production (Render), there's no dev proxy, so these are set to the real
// deployed service URLs via build-time env vars (VITE_API_BASE, VITE_DROID_BASE).
const BASE = import.meta.env.VITE_API_BASE || '/api'
const DROID_BASE = import.meta.env.VITE_DROID_BASE || '/droid/api'

function authHeaders(): Record<string, string> {
  const token = useAuthStore.getState().token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try {
      const body = await res.json()
      if (body?.message) message = body.message
      else if (body?.error) message = body.error
    } catch {
      // no JSON body, keep default message
    }
    throw new Error(message)
  }
  const text = await res.text()
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

export interface AuthResponse {
  token: string
  userId: string
  username: string
}

export function register(username: string, password: string): Promise<AuthResponse> {
  return fetch(`${BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  }).then(handle<AuthResponse>)
}

export function login(username: string, password: string): Promise<AuthResponse> {
  return fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  }).then(handle<AuthResponse>)
}

export interface Wallet {
  id: string
  userId: string
  currency: string
  balance: number
}

export function createWallet(userId: string, currency: string): Promise<Wallet> {
  return fetch(`${BASE}/wallets`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ userId, currency })
  }).then(handle<Wallet>)
}

export function getWallets(userId: string): Promise<Wallet[]> {
  return fetch(`${BASE}/wallets?userId=${userId}`, {
    headers: authHeaders()
  }).then(handle<Wallet[]>)
}

export function deposit(accountId: string, amount: number, reference: string): Promise<unknown> {
  const idempotencyKey = crypto.randomUUID()
  return fetch(`${BASE}/wallets/deposit`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
      ...authHeaders()
    },
    body: JSON.stringify({ accountId, amount, reference })
  }).then(handle)
}

export type OrderSide = 'BUY' | 'SELL'
// STOP covers both "buy stop" and "sell stop / stop-loss" - which one it is
// depends on `side`, not a separate value.
export type OrderType = 'LIMIT' | 'MARKET' | 'STOP'
export type OrderStatus = 'OPEN' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'TRIGGERED'

export interface OrderResponse {
  id: string
  userId: string
  tradingPair: string
  side: OrderSide
  orderType: OrderType
  price: number | null
  stopPrice: number | null
  quantity: number
  filledQuantity: number
  status: OrderStatus
  tradesExecuted: number
}

export function placeOrder(params: {
  userId: string
  tradingPair: string
  side: OrderSide
  orderType: OrderType
  price?: number
  stopPrice?: number
  quantity: number
}): Promise<OrderResponse> {
  const idempotencyKey = crypto.randomUUID()
  return fetch(`${BASE}/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
      ...authHeaders()
    },
    body: JSON.stringify(params)
  }).then(handle<OrderResponse>)
}

/** Lists the current user's own orders for one pair - used to draw price lines on the chart. */
export function getMyOrders(userId: string, tradingPair: string): Promise<OrderResponse[]> {
  return fetch(`${BASE}/orders?userId=${userId}&tradingPair=${tradingPair}`, {
    headers: authHeaders()
  }).then(handle<OrderResponse[]>)
}

export function cancelOrder(orderId: string): Promise<OrderResponse> {
  return fetch(`${BASE}/orders/${orderId}`, {
    method: 'DELETE',
    headers: authHeaders()
  }).then(handle<OrderResponse>)
}

export interface PriceLevel {
  price: number
  quantity: number
}

export interface OrderBookSnapshot {
  tradingPair: string
  bids: PriceLevel[]
  asks: PriceLevel[]
}

export function getOrderBook(tradingPair: string): Promise<OrderBookSnapshot> {
  return fetch(`${BASE}/orders/book/${tradingPair}`, {
    headers: authHeaders()
  }).then(handle<OrderBookSnapshot>)
}

export interface MarketSummary {
  tradingPair: string
  displayName: string
  lastPrice: number
  bestBid: number | null
  bestAsk: number | null
}

export function getMarkets(): Promise<MarketSummary[]> {
  return fetch(`${BASE}/markets`).then(handle<MarketSummary[]>)
}

// --- Python "Astromech" microservice: market data, candles, chat ---

export interface MarketTick {
  timestamp: number
  price: number
  smaShort: number
  smaLong: number
}

export interface MarketDataResponse {
  tradingPair: string
  currentPrice: number
  smaShort: number
  smaLong: number
  ticks: MarketTick[]
}

export function getMarketData(tradingPair: string, points = 200): Promise<MarketDataResponse> {
  return fetch(`${DROID_BASE}/market-data/${tradingPair}?points=${points}`).then(
    handle<MarketDataResponse>
  )
}

export interface Candle {
  timestamp: number
  open: number
  high: number
  low: number
  close: number
}

// Matches the backend's TIMEFRAMES keys exactly (market_data.py).
export type Timeframe = '1m' | '2h' | '3h' | '4h' | '1d' | '1mo' | '1y'

export const TIMEFRAME_OPTIONS: { value: Timeframe; label: string }[] = [
  { value: '1m', label: '1m' },
  { value: '2h', label: '2H' },
  { value: '3h', label: '3H' },
  { value: '4h', label: '4H' },
  { value: '1d', label: 'Daily' },
  { value: '1mo', label: 'Monthly' },
  { value: '1y', label: 'Yearly' }
]

export interface CandlesResponse {
  tradingPair: string
  timeframe: Timeframe
  currentPrice: number
  candles: Candle[]
}

export function getCandles(tradingPair: string, timeframe: Timeframe, points = 200): Promise<CandlesResponse> {
  return fetch(
    `${DROID_BASE}/market-data/${tradingPair}/candles?timeframe=${timeframe}&points=${points}`
  ).then(handle<CandlesResponse>)
}

export function sendChatMessage(message: string, userId: string): Promise<{ reply: string }> {
  return fetch(`${DROID_BASE}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ message, userId })
  }).then(handle<{ reply: string }>)
}
