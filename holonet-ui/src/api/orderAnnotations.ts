import { OrderResponse } from './client'

/**
 * Turns a user's open orders into horizontal line annotations for Chart.js
 * (via chartjs-plugin-annotation). Only resting orders that actually have a
 * meaningful price are shown — FILLED/CANCELLED orders are excluded since
 * they're no longer "live" on the book.
 *
 * Colors: green for BUY-side lines, red for SELL-side lines. STOP orders get
 * a dashed line (since they're not resting on the book yet, just armed to
 * trigger) instead of the solid line used for LIMIT orders.
 */
export function buildOrderAnnotations(orders: OrderResponse[]): Record<string, unknown> {
  const relevant = orders.filter(
    (o) => (o.status === 'OPEN' || o.status === 'PARTIALLY_FILLED') && (o.price != null || o.stopPrice != null)
  )

  const annotations: Record<string, unknown> = {}

  relevant.forEach((order) => {
    const isStop = order.orderType === 'STOP'
    const linePrice = isStop ? order.stopPrice : order.price
    if (linePrice == null) return

    const color = order.side === 'BUY' ? '#26a69a' : '#ef5350'
    const label = `${order.side} ${isStop ? 'stop' : 'limit'} ${order.quantity} @ ${linePrice}`

    annotations[`order-${order.id}`] = {
      type: 'line',
      yMin: linePrice,
      yMax: linePrice,
      borderColor: color,
      borderWidth: 1.5,
      borderDash: isStop ? [6, 4] : undefined,
      label: {
        display: true,
        content: label,
        position: 'end',
        backgroundColor: color,
        color: '#0b0e14',
        font: { size: 10, weight: 'bold' },
        padding: { top: 2, bottom: 2, left: 6, right: 6 }
      }
    }
  })

  return annotations
}
