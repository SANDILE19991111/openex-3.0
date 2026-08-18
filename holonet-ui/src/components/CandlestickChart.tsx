import { useEffect, useRef, useState } from 'react'
import {
  Chart as ChartJS,
  LinearScale,
  Tooltip,
  Legend,
  TimeScale,
  type ChartOptions
} from 'chart.js'
import { CandlestickController, CandlestickElement } from 'chartjs-chart-financial'
import 'chartjs-adapter-date-fns'
import { Chart } from 'react-chartjs-2'
import { CandlesResponse, TIMEFRAME_OPTIONS, Timeframe, getCandles } from '../api/client'

ChartJS.register(LinearScale, TimeScale, Tooltip, Legend, CandlestickController, CandlestickElement)

// '1m' is genuinely live, so poll it often. Longer timeframes are mostly
// synthetic/static aside from the final candle, so polling them is cheaper
// and less frequent — no point hammering the server for a "yearly" view.
const POLL_INTERVAL_MS: Record<Timeframe, number> = {
  '1m': 2000,
  '2h': 15000,
  '3h': 15000,
  '4h': 15000,
  '1d': 30000,
  '1mo': 60000,
  '1y': 60000
}

// Chart.js time-scale unit that makes sense for each timeframe's x-axis labels.
const TIME_UNIT: Record<Timeframe, 'minute' | 'hour' | 'day' | 'month' | 'year'> = {
  '1m': 'minute',
  '2h': 'hour',
  '3h': 'hour',
  '4h': 'hour',
  '1d': 'day',
  '1mo': 'month',
  '1y': 'year'
}

export default function CandlestickChart({ tradingPair }: { tradingPair: string }) {
  const [timeframe, setTimeframe] = useState<Timeframe>('1m')
  const [data, setData] = useState<CandlesResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const chartRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    setData(null)
    setError(null)

    async function poll() {
      try {
        const res = await getCandles(tradingPair, timeframe)
        if (!cancelled) setData(res)
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
    const timer = window.setInterval(poll, POLL_INTERVAL_MS[timeframe])

    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [tradingPair, timeframe])

  const timeframeButtons = (
    <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap', marginBottom: '0.75rem' }}>
      {TIMEFRAME_OPTIONS.map((opt) => (
        <button
          key={opt.value}
          onClick={() => setTimeframe(opt.value)}
          className={timeframe === opt.value ? 'primary' : ''}
          style={
            timeframe !== opt.value
              ? { background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--muted)', padding: '0.35rem 0.7rem', fontSize: '0.85rem' }
              : { padding: '0.35rem 0.7rem', fontSize: '0.85rem' }
          }
        >
          {opt.label}
        </button>
      ))}
    </div>
  )

  if (error) {
    return (
      <div>
        {timeframeButtons}
        <p className="error">{error}</p>
      </div>
    )
  }

  if (!data) {
    return (
      <div>
        {timeframeButtons}
        <p style={{ color: 'var(--muted)' }}>Loading candles…</p>
      </div>
    )
  }

  const chartData = {
    datasets: [
      {
        label: `${tradingPair} (${timeframe})`,
        data: data.candles.map((c) => ({
          x: c.timestamp * 1000,
          o: c.open,
          h: c.high,
          l: c.low,
          c: c.close
        })),
        color: {
          up: '#26a69a',
          down: '#ef5350',
          unchanged: '#8892a6'
        }
      }
    ]
  }

  const options: ChartOptions<'candlestick'> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    scales: {
      x: {
        type: 'time',
        time: { unit: TIME_UNIT[timeframe] },
        ticks: { color: '#8892a6', maxTicksLimit: 8 },
        grid: { color: '#232838' }
      },
      y: {
        ticks: { color: '#8892a6' },
        grid: { color: '#232838' }
      }
    },
    plugins: {
      legend: { labels: { color: '#8892a6' } }
    }
  }

  return (
    <div>
      {timeframeButtons}
      <div style={{ height: 280 }}>
        {/* @ts-expect-error chartjs-chart-financial's 'candlestick' type isn't in react-chartjs-2's built-in union */}
        <Chart ref={chartRef} type="candlestick" data={chartData} options={options} />
      </div>
      {(timeframe === '1d' || timeframe === '1mo' || timeframe === '1y') && (
        <p style={{ color: 'var(--muted)', fontSize: '0.75rem', marginTop: '0.5rem' }}>
          Note: {timeframe === '1d' ? 'daily' : timeframe === '1mo' ? 'monthly' : 'yearly'} history is
          simulated (the exchange only just launched) — it's a deterministic synthetic
          series anchored to the current live price, not real historical data.
        </p>
      )}
    </div>
  )
}
