package com.openex.core.matching

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class MatchingEngineTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var tradeRepository: TradeRepository
    private lateinit var engine: MatchingEngine

    private val pair = "BTC-USD"
    private val buyer = UUID.randomUUID()
    private val seller = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        orderRepository = mock(OrderRepository::class.java)
        tradeRepository = mock(TradeRepository::class.java)
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(tradeRepository.saveAll(any<List<Trade>>())).thenAnswer { it.arguments[0] }
        engine = MatchingEngine(orderRepository, tradeRepository)
    }

    private fun limitOrder(
        side: OrderSide,
        price: String,
        qty: String,
        userId: UUID = UUID.randomUUID()
    ) = Order(
        userId = userId,
        tradingPair = pair,
        side = side,
        orderType = OrderType.LIMIT,
        price = BigDecimal(price),
        quantity = BigDecimal(qty)
    )

    private fun marketOrder(side: OrderSide, qty: String, userId: UUID = UUID.randomUUID()) = Order(
        userId = userId,
        tradingPair = pair,
        side = side,
        orderType = OrderType.MARKET,
        quantity = BigDecimal(qty)
    )

    @Test
    fun `resting sell order with no counterparty stays OPEN`() {
        val sell = limitOrder(OrderSide.SELL, "100.00", "5")
        val result = engine.submit(sell)

        assertEquals(OrderStatus.OPEN, result.order.status)
        assertEquals(0, result.trades.size)
        assertEquals(BigDecimal.ZERO, result.order.filledQuantity)
    }

    @Test
    fun `matching buy fully fills a resting sell of equal quantity`() {
        engine.submit(limitOrder(OrderSide.SELL, "100.00", "5", seller))
        val buyResult = engine.submit(limitOrder(OrderSide.BUY, "100.00", "5", buyer))

        assertEquals(OrderStatus.FILLED, buyResult.order.status)
        assertEquals(1, buyResult.trades.size)
        assertEquals(0, BigDecimal("5").compareTo(buyResult.trades[0].quantity))
        assertEquals(0, BigDecimal("100.00").compareTo(buyResult.trades[0].price))
    }

    @Test
    fun `incoming order larger than resting liquidity partially fills and rests remainder`() {
        // Resting sell for only 3 units.
        engine.submit(limitOrder(OrderSide.SELL, "100.00", "3", seller))

        // Incoming buy wants 10 units -> only 3 available.
        val buyResult = engine.submit(limitOrder(OrderSide.BUY, "100.00", "10", buyer))

        assertEquals(OrderStatus.PARTIALLY_FILLED, buyResult.order.status)
        assertEquals(1, buyResult.trades.size)
        assertEquals(0, BigDecimal("3").compareTo(buyResult.order.filledQuantity))
        assertEquals(0, BigDecimal("7").compareTo(buyResult.order.remainingQuantity))

        // The remainder should now be resting on the book as an open bid.
        val snapshot = engine.snapshotFor(pair)
        assertTrue(snapshot.bids.any { it.quantity.compareTo(BigDecimal("7")) == 0 })
    }

    @Test
    fun `resting order smaller than incoming gets fully filled and removed from book`() {
        engine.submit(limitOrder(OrderSide.SELL, "100.00", "2", seller))
        val buyResult = engine.submit(limitOrder(OrderSide.BUY, "100.00", "5", buyer))

        assertEquals(0, BigDecimal("2").compareTo(buyResult.order.filledQuantity))
        assertEquals(OrderStatus.PARTIALLY_FILLED, buyResult.order.status)

        val snapshot = engine.snapshotFor(pair)
        assertTrue(snapshot.asks.isEmpty(), "Fully filled resting sell should be removed from the book")
    }

    @Test
    fun `price-time priority - best price fills first`() {
        // Two resting sells at different prices; cheaper should fill first.
        engine.submit(limitOrder(OrderSide.SELL, "101.00", "5", seller))
        engine.submit(limitOrder(OrderSide.SELL, "99.00", "5", UUID.randomUUID()))

        val buyResult = engine.submit(limitOrder(OrderSide.BUY, "101.00", "5", buyer))

        assertEquals(1, buyResult.trades.size)
        assertEquals(0, BigDecimal("99.00").compareTo(buyResult.trades[0].price))
    }

    @Test
    fun `market order takes available liquidity regardless of price`() {
        engine.submit(limitOrder(OrderSide.SELL, "150.00", "4", seller))
        val buyResult = engine.submit(marketOrder(OrderSide.BUY, "4", buyer))

        assertEquals(OrderStatus.FILLED, buyResult.order.status)
        assertEquals(0, BigDecimal("150.00").compareTo(buyResult.trades[0].price))
    }

    @Test
    fun `market order does not rest unfilled remainder when book runs dry`() {
        engine.submit(limitOrder(OrderSide.SELL, "150.00", "2", seller))
        val buyResult = engine.submit(marketOrder(OrderSide.BUY, "10", buyer))

        assertEquals(0, BigDecimal("2").compareTo(buyResult.order.filledQuantity))
        assertEquals(OrderStatus.PARTIALLY_FILLED, buyResult.order.status)

        val snapshot = engine.snapshotFor(pair)
        assertTrue(snapshot.bids.isEmpty(), "MARKET orders must never rest on the book")
    }

    @Test
    fun `cancel removes a resting order from the book`() {
        val sell = limitOrder(OrderSide.SELL, "100.00", "5", seller)
        engine.submit(sell)

        whenever(orderRepository.findById(sell.id)).thenReturn(java.util.Optional.of(sell))
        val cancelled = engine.cancel(sell.id)

        assertEquals(OrderStatus.CANCELLED, cancelled?.status)
        assertTrue(engine.snapshotFor(pair).asks.isEmpty())
    }
}
