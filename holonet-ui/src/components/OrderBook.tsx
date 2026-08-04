import { OrderBookSnapshot } from '../api/client'

export default function OrderBook({ book }: { book: OrderBookSnapshot | null }) {
  if (!book) {
    return <p style={{ color: 'var(--muted)' }}>Connecting to order book…</p>
  }

  // Asks: lowest first as-is from backend. Bids: highest first as-is from backend.
  return (
    <div className="orderbook">
      <div className="orderbook-side">
        <h3>Bids</h3>
        {book.bids.length === 0 && <p style={{ color: 'var(--muted)' }}>No bids</p>}
        {book.bids.map((level, i) => (
          <div className="orderbook-row bid" key={i}>
            <span>{level.price.toFixed(2)}</span>
            <span>{level.quantity}</span>
          </div>
        ))}
      </div>
      <div className="orderbook-side">
        <h3>Asks</h3>
        {book.asks.length === 0 && <p style={{ color: 'var(--muted)' }}>No asks</p>}
        {book.asks.map((level, i) => (
          <div className="orderbook-row ask" key={i}>
            <span>{level.price.toFixed(2)}</span>
            <span>{level.quantity}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
