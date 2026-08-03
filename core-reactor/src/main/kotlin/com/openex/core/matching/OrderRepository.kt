package com.openex.core.matching

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findAllByTradingPairAndStatusIn(tradingPair: String, statuses: List<OrderStatus>): List<Order>
    fun findAllByUserId(userId: UUID): List<Order>
}
