package com.openex.core.matching

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class OrderSide { BUY, SELL }
enum class OrderType { LIMIT, MARKET }
enum class OrderStatus { OPEN, PARTIALLY_FILLED, FILLED, CANCELLED }

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

    // Null for MARKET orders.
    @Column(precision = 18, scale = 8)
    var price: BigDecimal? = null,

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
}
