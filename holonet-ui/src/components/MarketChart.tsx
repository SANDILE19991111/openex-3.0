import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend
} from 'chart.js'
import { Line } from 'react-chartjs-2'
import { useLiveMarketData } from '../api/useLiveMarketData'
import PriceTicker from './PriceTicker'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend)

export default function MarketChart({ tradingPair }: { tradingPair: string }) {
  const { data, error, direction } = useLiveMarketData(tradingPair)

  if (error) {
    return <p className="error">{error}</p>
  }

  if (!data) {
    return <p style={{ color: 'var(--muted)' }}>Loading chart…</p>
  }

  const labels = data.ticks.map((t) =>
    new Date(t.timestamp * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  )

  const chartData = {
    labels,
    datasets: [
      {
        label: 'Price',
        data: data.ticks.map((t) => t.price),
        borderColor: '#5b8def',
        backgroundColor: 'transparent',
        pointRadius: 0,
        borderWidth: 2,
        tension: 0.15
      },
      {
        label: 'SMA (short)',
        data: data.ticks.map((t) => t.smaShort),
        borderColor: '#26a69a',
        backgroundColor: 'transparent',
        pointRadius: 0,
        borderWidth: 1,
        borderDash: [4, 4]
      },
      {
        label: 'SMA (long)',
        data: data.ticks.map((t) => t.smaLong),
        borderColor: '#ef5350',
        backgroundColor: 'transparent',
        pointRadius: 0,
        borderWidth: 1,
        borderDash: [4, 4]
      }
    ]
  }

  const options = {
    responsive: true,
    animation: false as const,
    interaction: { mode: 'index' as const, intersect: false },
    scales: {
      x: { ticks: { color: '#8892a6', maxTicksLimit: 8 }, grid: { color: '#232838' } },
      y: { ticks: { color: '#8892a6' }, grid: { color: '#232838' } }
    },
    plugins: {
      legend: { labels: { color: '#8892a6' } }
    }
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', marginBottom: '0.75rem' }}>
        <PriceTicker price={data.currentPrice} direction={direction} />
        <span style={{ color: 'var(--green)' }}>
          SMA10: {data.smaShort.toLocaleString(undefined, { maximumFractionDigits: 2 })}
        </span>
        <span style={{ color: 'var(--red)' }}>
          SMA30: {data.smaLong.toLocaleString(undefined, { maximumFractionDigits: 2 })}
        </span>
      </div>
      <Line data={chartData} options={options} />
    </div>
  )
}
