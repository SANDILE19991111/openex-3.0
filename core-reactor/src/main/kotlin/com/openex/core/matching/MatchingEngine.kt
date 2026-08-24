package com.openex.core.matching

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Price-time priority matching engine. One [OrderBook] per trading pair,
 * held in memory, matched under a per-pair lock so trades within a pair are
 * strictly serialized.
 *
 * STOP orders (buy stop / sell stop / stop-loss - all the same mechanism,
 * distinguished only by side) never touch the visible order book. They sit
 * in a per-pair watch list ([pendingStops]) until a trade executes at a
 * price that crosses their stopPrice, at which point they're pulled off the
 * watch list and submitted as a MARKET order. This is a simplified,
 * synchronous trigger model: stops are only checked immediately after a
 * trade in the SAME pair executes, not on a continuous background price
 * feed - good enough for a simulated exchange, not how a real venue's
 * risk engine would be built.
 */
@Service
class MatchingEngine(
    private val orderRepository: OrderRepository,
    private val tradeRepository: TradeRepository
) {
    private val books = ConcurrentHashMap<String, OrderBook>()
    private val pendingStops = ConcurrentHashMap<String, MutableList<Order>>()

    private fun bookFor(pair: String): OrderBook =
        books.computeIfAbsent(pair) { OrderBook(pair) }

    private fun stopsFor(pair: String): MutableList<Order> =
        pendingStops.computeIfAbsent(pair) { mutableListOf() }

    fun snapshotFor(pair: String) = bookFor(pair).snapshot()

    fun pendingStopsFor(pair: String): List<Order> = stopsFor(pair).toList()

    @Transactional
    fun submit(order: Order): MatchResult {
        if (order.orderType == OrderType.LIMIT) {
            require(order.price != null) { "LIMIT orders require a price" }
        }
        if (order.orderType == OrderType.STOP) {
            require(order.stopPrice != null) { "STOP orders require a stopPrice" }
        }
        require(order.quantity > BigDecimal.ZERO) { "Order quantity must be positive" }

        val book = bookFor(order.tradingPair)

        if (order.orderType == OrderType.STOP) {
            synchronized(book) {
                stopsFor(order.tradingPair).add(order)
                orderRepository.save(order)
            }
            return MatchResult(order, emptyList())
        }

        val trades = mutableListOf<Trade>()

        synchronized(book) {
            matchAgainstBook(order, book, trades)
            orderRepository.save(order)

            if (order.orderType == OrderType.LIMIT && !order.isFullyFilled() &&
                order.status != OrderStatus.CANCELLED
            ) {
                book.addResting(order)
            }

            if (trades.isNotEmpty()) {
                val lastPrice = trades.last().price
                triggerStops(order.tradingPair, lastPrice, book, trades)
            }
        }

        tradeRepository.saveAll(trades)
        return MatchResult(order, trades)
    }

    private fun triggerStops(tradingPair: String, lastPrice: BigDecimal, book: OrderBook, trades: MutableList<Trade>) {
        val stops = stopsFor(tradingPair)
        val toTrigger = stops.filter { it.shouldTrigger(lastPrice) }
        if (toTrigger.isEmpty()) return

        toTrigger.forEach { stopOrder ->
            stops.remove(stopOrder)
            stopOrder.status = OrderStatus.TRIGGERED
            stopOrder.updatedAt = Instant.now()
            orderRepository.save(stopOrder)

            val marketOrder = Order(
                id = UUID.randomUUID(),
                userId = stopOrder.userId,
                tradingPair = stopOrder.tradingPair,
                side = stopOrder.side,
                orderType = OrderType.MARKET,
                quantity = stopOrder.remainingQuantity
            )
            matchAgainstBook(marketOrder, book, trades)
            orderRepository.save(marketOrder)

            if (trades.isNotEmpty()) {
                triggerStops(tradingPair, trades.last().price, book, trades)
            }
        }
    }

    private fun matchAgainstBook(incoming: Order, book: OrderBook, trades: MutableList<Trade>) {
        val oppositeLevels = book.oppositeLevels(incoming.side)

        while (incoming.remainingQuantity > BigDecimal.ZERO && oppositeLevels.isNotEmpty()) {
            val bestEntry = oppositeLevels.firstEntry()
            val bestPrice = bestEntry.key
            val queue = bestEntry.value

            if (incoming.orderType == OrderType.LIMIT) {
                val crosses = if (incoming.side == OrderSide.BUY) {
                    incoming.price!! >= bestPrice
                } else {
                    incoming.price!! <= bestPrice
                }
                if (!crosses) break
            }

            if (queue.isEmpty()) {
                oppositeLevels.remove(bestPrice)
                continue
            }

            val resting = queue.first()
            val fillQty = minOf(incoming.remainingQuantity, resting.remainingQuantity)

            val buyOrderId = if (incoming.side == OrderSide.BUY) incoming.id else resting.id
            val sellOrderId = if (incoming.side == OrderSide.SELL) incoming.id else resting.id

            trades.add(
                Trade(
                    tradingPair = incoming.tradingPair,
                    buyOrderId = buyOrderId,
                    sellOrderId = sellOrderId,
                    price = bestPrice,
                    quantity = fillQty
                )
            )

            incoming.filledQuantity = incoming.filledQuantity.add(fillQty)
            resting.filledQuantity = resting.filledQuantity.add(fillQty)
            resting.updatedAt = Instant.now()

            if (resting.isFullyFilled()) {
                resting.status = OrderStatus.FILLED
                queue.removeFirst()
                if (queue.isEmpty()) oppositeLevels.remove(bestPrice)
            } else {
                resting.status = OrderStatus.PARTIALLY_FILLED
            }
            orderRepository.save(resting)
        }

        incoming.status = when {
            incoming.isFullyFilled() -> OrderStatus.FILLED
            incoming.filledQuantity > BigDecimal.ZERO -> OrderStatus.PARTIALLY_FILLED
            else -> OrderStatus.OPEN
        }
        incoming.updatedAt = Instant.now()
    }

    fun cancel(orderId: UUID): Order? {
        val order = orderRepository.findById(orderId).orElse(null) ?: return null
        if (order.status == OrderStatus.FILLED || order.status == OrderStatus.CANCELLED) return order

        val book = bookFor(order.tradingPair)
        synchronized(book) {
            if (order.orderType == OrderType.STOP) {
                stopsFor(order.tradingPair).remove(order)
            } else {
                book.removeResting(order)
            }
            order.status = OrderStatus.CANCELLED
            order.updatedAt = Instant.now()
            orderRepository.save(order)
        }
        return order
    }
}

data class MatchResult(val order: Order, val trades: List<Trade>)
