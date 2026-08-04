import { FormEvent, useEffect, useState } from 'react'
import { OrderBookSnapshot, OrderResponse, OrderSide, OrderType, getOrderBook, placeOrder } from '../api/client'
import { subscribeOrderBook } from '../api/ws'
import { useAuthStore } from '../store/authStore'
import OrderBookComponent from '../components/OrderBook'

const TRADING_PAIR = 'BTC-USD'

export default function TradingPage() {
  const userId = useAuthStore((s) => s.userId)!
  const [book, setBook] = useState<OrderBookSnapshot | null>(null)
  const [recentOrders, setRecentOrders] = useState<OrderResponse[]>([])

  const [side, setSide] = useState<OrderSide>('BUY')
  const [orderType, setOrderType] = useState<OrderType>('LIMIT')
  const [price, setPrice] = useState('')
  const [quantity, setQuantity] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Initial snapshot via REST, then live updates via WebSocket.
  useEffect(() => {
    getOrderBook(TRADING_PAIR).then(setBook).catch(() => {})
    const unsubscribe = subscribeOrderBook(TRADING_PAIR, setBook)
    return unsubscribe
  }, [])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const result = await placeOrder({
        userId,
        tradingPair: TRADING_PAIR,
        side,
        orderType,
        price: orderType === 'LIMIT' ? Number(price) : undefined,
        quantity: Number(quantity)
      })
      setRecentOrders((prev) => [result, ...prev].slice(0, 10))
      setQuantity('')
      // Order book will update itself via the WebSocket broadcast — no manual refresh needed.
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Order failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page">
      <div className="panel">
        <h2>{TRADING_PAIR} Order Book</h2>
        <OrderBookComponent book={book} />
      </div>

      <div className="panel">
        <h2>Place order</h2>
        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <label>
              Side
              <select value={side} onChange={(e) => setSide(e.target.value as OrderSide)}>
                <option value="BUY">Buy</option>
                <option value="SELL">Sell</option>
              </select>
            </label>
            <label>
              Type
              <select value={orderType} onChange={(e) => setOrderType(e.target.value as OrderType)}>
                <option value="LIMIT">Limit</option>
                <option value="MARKET">Market</option>
              </select>
            </label>
            {orderType === 'LIMIT' && (
              <label>
                Price
                <input
                  type="number"
                  min="0"
                  step="any"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  required
                />
              </label>
            )}
            <label>
              Quantity
              <input
                type="number"
                min="0"
                step="any"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                required
              />
            </label>
          </div>
          <button type="submit" className={side === 'BUY' ? 'buy' : 'sell'} disabled={submitting}>
            {submitting ? 'Placing…' : side === 'BUY' ? 'Buy' : 'Sell'} {TRADING_PAIR}
          </button>
          {error && <div className="error">{error}</div>}
        </form>
      </div>

      <div className="panel">
        <h2>Recent orders (this session)</h2>
        {recentOrders.length === 0 && <p style={{ color: 'var(--muted)' }}>No orders placed yet.</p>}
        {recentOrders.map((o) => (
          <div className="wallet-card" key={o.id}>
            <span>
              {o.side} {o.quantity} @ {o.price ?? 'MARKET'}
            </span>
            <span className={`status-badge status-${o.status}`}>{o.status}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
