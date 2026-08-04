package com.openex.core.matching

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode

data class MarketSummary(
    val tradingPair: String,
    val displayName: String,
    val lastPrice: BigDecimal,
    val bestBid: BigDecimal?,
    val bestAsk: BigDecimal?
)

@RestController
@RequestMapping("/api/markets")
class MarketsController(
    private val matchingEngine: MatchingEngine
) {

    @GetMapping
    fun listMarkets(): List<MarketSummary> = SupportedPairs.ALL.map { pair ->
        val snapshot = matchingEngine.snapshotFor(pair.tradingPair)
        val bestBid = snapshot.bids.firstOrNull()?.price
        val bestAsk = snapshot.asks.firstOrNull()?.price
        val lastPrice = when {
            bestBid != null && bestAsk != null ->
                bestBid.add(bestAsk).divide(BigDecimal(2), 8, RoundingMode.HALF_UP)
            bestBid != null -> bestBid
            bestAsk != null -> bestAsk
            else -> pair.startingPrice
        }
        MarketSummary(pair.tradingPair, pair.displayName, lastPrice, bestBid, bestAsk)
    }
}
