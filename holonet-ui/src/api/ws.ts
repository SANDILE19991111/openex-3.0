import { Client, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { OrderBookSnapshot } from '../api/client'

let client: Client | null = null

function getClient(): Client {
  if (client) return client

  client = new Client({
    webSocketFactory: () => new SockJS('/ws') as unknown as WebSocket,
    reconnectDelay: 3000,
    debug: () => {} // silence verbose STOMP frame logs
  })
  client.activate()
  return client
}

/**
 * Subscribes to live order book snapshots for a trading pair. Returns an
 * unsubscribe function — call it in a React effect cleanup.
 */
export function subscribeOrderBook(
  tradingPair: string,
  onSnapshot: (snapshot: OrderBookSnapshot) => void
): () => void {
  const c = getClient()
  let subscription: { unsubscribe: () => void } | null = null

  const trySubscribe = () => {
    subscription = c.subscribe(`/topic/orderbook/${tradingPair}`, (message: IMessage) => {
      onSnapshot(JSON.parse(message.body) as OrderBookSnapshot)
    })
  }

  if (c.connected) {
    trySubscribe()
  } else {
    c.onConnect = () => trySubscribe()
  }

  return () => {
    subscription?.unsubscribe()
  }
}

export interface TradeEvent {
  id: string
  tradingPair: string
  buyOrderId: string
  sellOrderId: string
  price: number
  quantity: number
  executedAt: string
}

export function subscribeTrades(
  tradingPair: string,
  onTrade: (trade: TradeEvent) => void
): () => void {
  const c = getClient()
  let subscription: { unsubscribe: () => void } | null = null

  const trySubscribe = () => {
    subscription = c.subscribe(`/topic/trades/${tradingPair}`, (message: IMessage) => {
      onTrade(JSON.parse(message.body) as TradeEvent)
    })
  }

  if (c.connected) {
    trySubscribe()
  } else {
    c.onConnect = () => trySubscribe()
  }

  return () => {
    subscription?.unsubscribe()
  }
}
