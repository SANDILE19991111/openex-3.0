import { useAuthStore } from '../store/authStore'

const BASE = '/api'

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
export type OrderType = 'LIMIT' | 'MARKET'
export type OrderStatus = 'OPEN' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED'

export interface OrderResponse {
  id: string
  userId: string
  tradingPair: string
  side: OrderSide
  orderType: OrderType
  price: number | null
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
