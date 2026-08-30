import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { OrderResponse, cancelOrder, getMyOrders } from '../api/client'
import { useAuthStore } from '../store/authStore'

const OPEN_STATUSES = new Set(['OPEN', 'PARTIALLY_FILLED'])

export default function OrderHistoryPage() {
  const userId = useAuthStore((s) => s.userId)!
  const [orders, setOrders] = useState<OrderResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [cancellingId, setCancellingId] = useState<string | null>(null)

  async function refresh() {
    setError(null)
    try {
      const result = await getMyOrders(userId)
      setOrders(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load order history')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleCancel(orderId: string) {
    setCancellingId(orderId)
    setError(null)
    try {
      await cancelOrder(orderId)
      await refresh()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to cancel order')
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <div className="page">
      <div className="panel">
        <h2>Order History</h2>
        {loading && <p style={{ color: 'var(--muted)' }}>Loading…</p>}
        {error && <div className="error">{error}</div>}
        {!loading && orders.length === 0 && (
          <p style={{ color: 'var(--muted)' }}>No orders yet — place one from the Trading page.</p>
        )}
        {!loading && orders.length > 0 && (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', color: 'var(--muted)', fontSize: '0.8rem' }}>
                <th style={{ padding: '0.5rem 0.4rem' }}>Pair</th>
                <th style={{ padding: '0.5rem 0.4rem' }}>Side</th>
                <th style={{ padding: '0.5rem 0.4rem' }}>Type</th>
                <th style={{ padding: '0.5rem 0.4rem', textAlign: 'right' }}>Price</th>
                <th style={{ padding: '0.5rem 0.4rem', textAlign: 'right' }}>Qty</th>
                <th style={{ padding: '0.5rem 0.4rem', textAlign: 'right' }}>Filled</th>
                <th style={{ padding: '0.5rem 0.4rem' }}>Status</th>
                <th style={{ padding: '0.5rem 0.4rem' }}></th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id} style={{ borderTop: '1px solid var(--border)' }}>
                  <td style={{ padding: '0.6rem 0.4rem' }}>
                    <Link to={`/trading/${o.tradingPair}`}>{o.tradingPair}</Link>
                  </td>
                  <td style={{ padding: '0.6rem 0.4rem', color: o.side === 'BUY' ? 'var(--green)' : 'var(--red)' }}>
                    {o.side}
                  </td>
                  <td style={{ padding: '0.6rem 0.4rem', color: 'var(--muted)' }}>
                    {o.orderType}
                    {o.orderType === 'STOP' && o.stopPrice != null ? ` @ ${o.stopPrice}` : ''}
                  </td>
                  <td style={{ padding: '0.6rem 0.4rem', textAlign: 'right' }}>
                    {o.price ?? 'MARKET'}
                  </td>
                  <td style={{ padding: '0.6rem 0.4rem', textAlign: 'right' }}>{o.quantity}</td>
                  <td style={{ padding: '0.6rem 0.4rem', textAlign: 'right' }}>{o.filledQuantity}</td>
                  <td style={{ padding: '0.6rem 0.4rem' }}>
                    <span className={`status-badge status-${o.status}`}>{o.status}</span>
                  </td>
                  <td style={{ padding: '0.6rem 0.4rem', textAlign: 'right' }}>
                    {OPEN_STATUSES.has(o.status) && (
                      <button
                        className="sell"
                        style={{ padding: '0.3rem 0.6rem', fontSize: '0.8rem' }}
                        disabled={cancellingId === o.id}
                        onClick={() => handleCancel(o.id)}
                      >
                        {cancellingId === o.id ? 'Cancelling…' : 'Cancel'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
