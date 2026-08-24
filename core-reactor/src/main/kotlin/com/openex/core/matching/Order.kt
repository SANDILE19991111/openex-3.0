package com.openex.core.matching

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class OrderSide { BUY, SELL }

// STOP covers both "buy stop" (side=BUY) and "sell stop" / "stop-loss" (side=SELL) -
// which one it is depends entirely on the side, not a separate enum value. A STOP
// order does NOT rest on the visible order book; it sits in a separate watch list
// until the last traded price crosses stopPrice, at which point it's triggered and
// submitted as a MARKET order.
enum class OrderType { LIMIT, MARKET, STOP }

enum class OrderStatus { OPEN, PARTIALLY_FILLED, FILLED, CANCELLED, TRIGGERED }

@Entity
@Table(name = "orders")
class Order(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "trading_pair", nullable = false)
    val tradingPair: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val side: OrderSide,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    val orderType: OrderType,

    // Null for MARKET orders and for STOP orders before they trigger (a STOP order
    // triggers into a MARKET order, so it never needs its own execution price).
    @Column(precision = 18, scale = 8)
    var price: BigDecimal? = null,

    // Only set for STOP orders: the trade price that triggers this order.
    @Column(name = "stop_price", precision = 18, scale = 8)
    var stopPrice: BigDecimal? = null,

    @Column(nullable = false, precision = 18, scale = 8)
    val quantity: BigDecimal,

    @Column(name = "filled_quantity", nullable = false, precision = 18, scale = 8)
    var filledQuantity: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.OPEN,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    val remainingQuantity: BigDecimal
        get() = quantity.subtract(filledQuantity)

    fun isFullyFilled(): Boolean = filledQuantity.compareTo(quantity) >= 0

    /** Whether a trade at [lastPrice] should trigger this resting STOP order. */
    fun shouldTrigger(lastPrice: BigDecimal): Boolean {
        val trigger = stopPrice ?: return false
        return if (side == OrderSide.BUY) {
            lastPrice >= trigger   // buy stop: triggers on price rising through the stop
        } else {
            lastPrice <= trigger   // sell stop / stop-loss: triggers on price falling through the stop
        }
    }

    // Overridden by id: cancel() reloads an order via JPA (a different object
    // instance from the one resting in-memory in the book/stop watch list),
    // and the in-memory collections need to recognize it as "the same order"
    // to actually remove it - default reference equality would silently fail.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Order) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
