import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { MarketSummary, getMarkets } from '../api/client'

export default function MarketsPage() {
  const [markets, setMarkets] = useState<MarketSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const data = await getMarkets()
        if (!cancelled) setMarkets(data)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load markets')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    // Refresh every 5s so prices feel alive even without a full realtime feed here.
    const interval = setInterval(load, 5000)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [])

  return (
    <div className="page">
      <div className="panel">
        <h2>Markets</h2>
        {loading && <p style={{ color: 'var(--muted)' }}>Loading markets…</p>}
        {error && <div className="error">{error}</div>}
        {!loading && !error && (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', color: 'var(--muted)', fontSize: '0.8rem' }}>
                <th style={{ padding: '0.5rem 0.4rem' }}>Pair</th>
                <th style={{ padding: '0.5rem 0.4rem' }}>Name</th>
                <th style={{ padding: '0.5rem 0.4rem', textAlign: 'right' }}>Last Price</th>
                <th style={{ padding: '0.5rem 0.4rem', textAlign: 'right' }}>Bid</th>
                <th style={{ padding: '0.5rem 0.4rem', textAlign: 'right' }}>Ask</th>
                <th style={{ padding: '0.5rem 0.4rem' }}></th>
              </tr>
            </thead>
            <tbody>
              {markets.map((m) => (
                <tr key={m.tradingPair} style={{ borderTop: '1px solid var(--border)' }}>
                  <td style={{ padding: '0.6rem 0.4rem', fontWeight: 600 }}>{m.tradingPair}</td>
                  <td style={{ padding: '0.6rem 0.4rem', color: 'var(--muted)' }}>{m.displayName}</td>
                  <td style={{ padding: '0.6rem 0.4rem', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                    {m.lastPrice.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                  </td>
                  <td style={{ padding: '0.6rem 0.4rem', textAlign: 'right', color: 'var(--green)' }}>
                    {m.bestBid?.toLocaleString(undefined, { maximumFractionDigits: 2 }) ?? '—'}
                  </td>
                  <td style={{ padding: '0.6rem 0.4rem', textAlign: 'right', color: 'var(--red)' }}>
                    {m.bestAsk?.toLocaleString(undefined, { maximumFractionDigits: 2 }) ?? '—'}
                  </td>
                  <td style={{ padding: '0.6rem 0.4rem', textAlign: 'right' }}>
                    <Link to={`/trading/${m.tradingPair}`}>
                      <button className="primary">Trade</button>
                    </Link>
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
