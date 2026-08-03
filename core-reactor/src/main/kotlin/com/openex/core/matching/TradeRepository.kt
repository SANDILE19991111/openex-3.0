package com.openex.core.matching

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TradeRepository : JpaRepository<Trade, UUID> {
    fun findAllByTradingPairOrderByExecutedAtDesc(tradingPair: String): List<Trade>
}
