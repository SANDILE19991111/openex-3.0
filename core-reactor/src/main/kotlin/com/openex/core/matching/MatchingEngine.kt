package com.openex.core.matching

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Price-time priority matching engine. One [OrderBook] per trading pair,
 * held in memory. Each pair's book is matched under its own lock so trades
 * within a pair are strictly serialized (no two threads can match against
 * the same book concurrently), while different pairs can match in parallel.
 *
 * Matching rules:
 *  - LIMIT BUY matches any resting SELL priced at or below the buy price.
 *  - LIMIT SELL matches any resting BUY priced at or above the sell price.
 *  - MARKET orders match at whatever price the book offers, walking levels
 *    until filled or the book runs out of liquidity on that side.
 *  - Trade execution price is always the RESTING order's price (the order
 *    that was already on the book), not the incoming order's price.
 *  - Any unfilled remainder of a LIMIT order rests on the book. Unfilled
 *    remainder of a MARKET order is simply not filled (no resting).
 */
@Service
class MatchingEngine(
    private val orderRepository: OrderRepository,
    private val tradeRepository: TradeRepository
) {
    private val books = ConcurrentHashMap<String, OrderBook>()

    private fun bookFor(pair: String): OrderBook =
        books.computeIfAbsent(pair) { OrderBook(pair) }

    fun snapshotFor(pair: String) = bookFor(pair).snapshot()

    /**
     * Submits a new order to the engine. Persists the order, matches it
     * against the resting book, records any resulting trades, and rests
     * the remainder (LIMIT only). Returns the final persisted order plus
     * the list of trades this specific submission generated.
     */
    @Transactional
    fun submit(order: Order): MatchResult {
        if (order.orderType == OrderType.LIMIT) {
            require(order.price != null) { "LIMIT orders require a price" }
        }
        require(order.quantity > BigDecimal.ZERO) { "Order quantity must be positive" }

        val book = bookFor(order.tradingPair)
        val trades = mutableListOf<Trade>()

        synchronized(book) {
            matchAgainstBook(order, book, trades)

            // Persist the incoming order in its post-match state.
            orderRepository.save(order)

            // Rest any unfilled remainder — LIMIT only.
            if (order.orderType == OrderType.LIMIT && !order.isFullyFilled() &&
                order.status != OrderStatus.CANCELLED
            ) {
                book.addResting(order)
            }
        }

        tradeRepository.saveAll(trades)
        return MatchResult(order, trades)
    }

    private fun matchAgainstBook(incoming: Order, book: OrderBook, trades: MutableList<Trade>) {
        val oppositeLevels = book.oppositeLevels(incoming.side)

        while (incoming.remainingQuantity > BigDecimal.ZERO && oppositeLevels.isNotEmpty()) {
            val bestEntry = oppositeLevels.firstEntry()
            val bestPrice = bestEntry.key
            val queue = bestEntry.value

            // For LIMIT orders, stop once prices no longer cross.
            if (incoming.orderType == OrderType.LIMIT) {
                val crosses = if (incoming.side == OrderSide.BUY) {
                    incoming.price!! >= bestPrice
                } else {
                    incoming.price!! <= bestPrice
                }
                if (!crosses) break
            }
            // MARKET orders always cross — they take whatever price is available.

            if (queue.isEmpty()) {
                oppositeLevels.remove(bestPrice)
                continue
            }

            val resting = queue.first()
            val fillQty = minOf(incoming.remainingQuantity, resting.remainingQuantity)

            // Trade executes at the resting order's price (price-time priority convention).
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
            resting.updatedAt = java.time.Instant.now()

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
        incoming.updatedAt = java.time.Instant.now()
    }

    fun cancel(orderId: UUID): Order? {
        val order = orderRepository.findById(orderId).orElse(null) ?: return null
        if (order.status == OrderStatus.FILLED || order.status == OrderStatus.CANCELLED) return order

        val book = bookFor(order.tradingPair)
        synchronized(book) {
            book.removeResting(order)
            order.status = OrderStatus.CANCELLED
            order.updatedAt = java.time.Instant.now()
            orderRepository.save(order)
        }
        return order
    }
}

data class MatchResult(val order: Order, val trades: List<Trade>)
