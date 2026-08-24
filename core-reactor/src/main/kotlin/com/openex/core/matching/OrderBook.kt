package com.openex.core.matching

import java.math.BigDecimal
import java.util.TreeMap

class OrderBook(val tradingPair: String) {

    val buyLevels = TreeMap<BigDecimal, ArrayDeque<Order>>(Comparator.reverseOrder())
    val sellLevels = TreeMap<BigDecimal, ArrayDeque<Order>>(Comparator.naturalOrder())

    fun bestBid(): BigDecimal? = if (buyLevels.isEmpty()) null else buyLevels.firstKey()
    fun bestAsk(): BigDecimal? = if (sellLevels.isEmpty()) null else sellLevels.firstKey()

    fun addResting(order: Order) {
        val price = order.price ?: error("Only LIMIT orders with a price can rest on the book")
        val levels = if (order.side == OrderSide.BUY) buyLevels else sellLevels
        levels.computeIfAbsent(price) { ArrayDeque() }.addLast(order)
    }

    fun removeResting(order: Order) {
        val price = order.price ?: return
        val levels = if (order.side == OrderSide.BUY) buyLevels else sellLevels
        val queue = levels[price] ?: return
        queue.remove(order)
        if (queue.isEmpty()) levels.remove(price)
    }

    fun oppositeLevels(side: OrderSide): TreeMap<BigDecimal, ArrayDeque<Order>> =
        if (side == OrderSide.BUY) sellLevels else buyLevels

    fun snapshot(): OrderBookSnapshot = OrderBookSnapshot(
        tradingPair = tradingPair,
        bids = buyLevels.entries.map { (price, orders) ->
            PriceLevel(price, orders.sumOf { it.remainingQuantity })
        },
        asks = sellLevels.entries.map { (price, orders) ->
            PriceLevel(price, orders.sumOf { it.remainingQuantity })
        }
    )
}

data class PriceLevel(val price: BigDecimal, val quantity: BigDecimal)

data class OrderBookSnapshot(
    val tradingPair: String,
    val bids: List<PriceLevel>,
    val asks: List<PriceLevel>
)
