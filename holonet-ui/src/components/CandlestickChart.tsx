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
import { CandlesResponse, getCandles } from '../api/client'

ChartJS.register(LinearScale, TimeScale, Tooltip, Legend, CandlestickController, CandlestickElement)

const POLL_INTERVAL_MS = 2000

export default function CandlestickChart({ tradingPair }: { tradingPair: string }) {
  const [data, setData] = useState<CandlesResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const chartRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    setData(null)
    setError(null)

    async function poll() {
      try {
        const res = await getCandles(tradingPair)
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
    const interval = setInterval(poll, POLL_INTERVAL_MS)

    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [tradingPair])

  if (error) {
    return <p className="error">{error}</p>
  }

  if (!data) {
    return <p style={{ color: 'var(--muted)' }}>Loading candles…</p>
  }

  const chartData = {
    datasets: [
      {
        label: tradingPair,
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
        time: { unit: 'minute' },
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
    <div style={{ height: 280 }}>
      {/* @ts-expect-error chartjs-chart-financial's 'candlestick' type isn't in react-chartjs-2's built-in union */}
      <Chart ref={chartRef} type="candlestick" data={chartData} options={options} />
    </div>
  )
}
