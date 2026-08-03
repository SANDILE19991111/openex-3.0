package com.openex.core.matching

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "trades")
class Trade(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "trading_pair", nullable = false)
    val tradingPair: String,

    @Column(name = "buy_order_id", nullable = false)
    val buyOrderId: UUID,

    @Column(name = "sell_order_id", nullable = false)
    val sellOrderId: UUID,

    @Column(nullable = false, precision = 18, scale = 8)
    val price: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 8)
    val quantity: BigDecimal,

    @Column(name = "executed_at", nullable = false, updatable = false)
    val executedAt: Instant = Instant.now()
)
