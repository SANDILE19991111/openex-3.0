import { useEffect, useRef, useState } from 'react'
import { MarketDataResponse, getMarketData } from '../api/client'

export type PriceDirection = 'up' | 'down' | 'flat'

interface LiveMarketData {
  data: MarketDataResponse | null
  error: string | null
  direction: PriceDirection
}

const POLL_INTERVAL_MS = 2000

/**
 * Polls the market data endpoint on an interval so the price genuinely
 * moves on screen, and tracks whether the latest tick moved the price up
 * or down (for a green/red flash on the ticker).
 */
export function useLiveMarketData(tradingPair: string): LiveMarketData {
  const [data, setData] = useState<MarketDataResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [direction, setDirection] = useState<PriceDirection>('flat')
  const lastPriceRef = useRef<number | null>(null)

  useEffect(() => {
    let cancelled = false
    setData(null)
    setError(null)
    setDirection('flat')
    lastPriceRef.current = null

    async function poll() {
      try {
        const res = await getMarketData(tradingPair)
        if (cancelled) return

        if (lastPriceRef.current !== null) {
          if (res.currentPrice > lastPriceRef.current) setDirection('up')
          else if (res.currentPrice < lastPriceRef.current) setDirection('down')
          else setDirection('flat')
        }
        lastPriceRef.current = res.currentPrice
        setData(res)
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? `Chart unavailable: ${err.message}`
              : 'Chart unavailable — is the Astromech service running?'
          )
        }
      }
    }

    poll()
    const interval = setInterval(poll, POLL_INTERVAL_MS)

    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [tradingPair])

  return { data, error, direction }
}
