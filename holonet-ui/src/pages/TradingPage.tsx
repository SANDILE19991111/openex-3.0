import { FormEvent, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { OrderBookSnapshot, OrderResponse, OrderSide, OrderType, getOrderBook, placeOrder } from '../api/client'
import { subscribeOrderBook } from '../api/ws'
import { useAuthStore } from '../store/authStore'
import OrderBookComponent from '../components/OrderBook'
import MarketChart from '../components/MarketChart'
import CandlestickChart from '../components/CandlestickChart'

type ChartView = 'line' | 'candles'

export default function TradingPage() {
  const { pair } = useParams<{ pair: string }>()
  const tradingPair = pair ?? 'BTC-USD'
  const userId = useAuthStore((s) => s.userId)!
  const [book, setBook] = useState<OrderBookSnapshot | null>(null)
  const [recentOrders, setRecentOrders] = useState<OrderResponse[]>([])
  const [chartView, setChartView] = useState<ChartView>('line')

  const [side, setSide] = useState<OrderSide>('BUY')
  const [orderType, setOrderType] = useState<OrderType>('LIMIT')
  const [price, setPrice] = useState('')
  const [quantity, setQuantity] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Initial snapshot via REST, then live updates via WebSocket. Re-subscribes
  // whenever the trading pair changes (e.g. navigating from the Markets page).
  useEffect(() => {
    setBook(null)
    getOrderBook(tradingPair).then(setBook).catch(() => {})
    const unsubscribe = subscribeOrderBook(tradingPair, setBook)
    return unsubscribe
  }, [tradingPair])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const result = await placeOrder({
        userId,
        tradingPair,
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
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ margin: 0 }}>{tradingPair} Price Chart</h2>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              className={chartView === 'line' ? 'primary' : ''}
              style={chartView !== 'line' ? { background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--muted)' } : undefined}
              onClick={() => setChartView('line')}
            >
              Line
            </button>
            <button
              className={chartView === 'candles' ? 'primary' : ''}
              style={chartView !== 'candles' ? { background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--muted)' } : undefined}
              onClick={() => setChartView('candles')}
            >
              Candles
            </button>
          </div>
        </div>
        {chartView === 'line' ? (
          <MarketChart tradingPair={tradingPair} />
        ) : (
          <CandlestickChart tradingPair={tradingPair} />
        )}
      </div>

      <div className="panel">
        <h2>{tradingPair} Order Book</h2>
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
            {submitting ? 'Placing…' : side === 'BUY' ? 'Buy' : 'Sell'} {tradingPair}
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
