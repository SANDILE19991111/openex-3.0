import { useEffect, useState } from 'react'
import { PriceDirection } from '../api/useLiveMarketData'

export default function PriceTicker({
  price,
  direction
}: {
  price: number
  direction: PriceDirection
}) {
  const [flash, setFlash] = useState(false)

  useEffect(() => {
    if (direction === 'flat') return
    setFlash(true)
    const timeout = setTimeout(() => setFlash(false), 400)
    return () => clearTimeout(timeout)
  }, [price, direction])

  const color = direction === 'up' ? 'var(--green)' : direction === 'down' ? 'var(--red)' : 'var(--text)'
  const arrow = direction === 'up' ? '▲' : direction === 'down' ? '▼' : ''

  return (
    <span
      style={{
        color,
        fontWeight: 700,
        fontSize: '1.4rem',
        fontVariantNumeric: 'tabular-nums',
        transition: 'opacity 0.15s ease',
        opacity: flash ? 0.55 : 1,
        display: 'inline-flex',
        alignItems: 'center',
        gap: '0.35rem'
      }}
    >
      {price.toLocaleString(undefined, { maximumFractionDigits: 2 })}
      {arrow && <span style={{ fontSize: '0.9rem' }}>{arrow}</span>}
    </span>
  )
}
