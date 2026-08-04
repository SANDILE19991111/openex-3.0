package com.openex.core.websocket

import com.openex.core.matching.OrderBookSnapshot
import com.openex.core.matching.Trade
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class OrderBookBroadcaster(
    private val messagingTemplate: SimpMessagingTemplate
) {
    /**
     * Pushes a snapshot to everyone subscribed to /topic/orderbook/{tradingPair}.
     * Called by the matching engine after every order submission that could
     * have changed the book (new resting order, a fill, a cancellation).
     */
    fun broadcast(snapshot: OrderBookSnapshot) {
        messagingTemplate.convertAndSend("/topic/orderbook/${snapshot.tradingPair}", snapshot)
    }

    /** Pushes each executed trade to /topic/trades/{tradingPair} as it happens. */
    fun broadcastTrades(tradingPair: String, trades: List<Trade>) {
        if (trades.isEmpty()) return
        trades.forEach { trade ->
            messagingTemplate.convertAndSend("/topic/trades/$tradingPair", trade)
        }
    }
}
