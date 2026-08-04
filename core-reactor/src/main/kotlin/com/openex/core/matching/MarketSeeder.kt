package com.openex.core.matching

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Seeds each supported trading pair with one resting bid and one resting ask
 * around its starting price, so the Markets page shows a live-looking price
 * immediately on boot instead of an empty book. The in-memory order book
 * resets every restart (MatchingEngine holds it in memory), so this reseeds
 * every time the app starts.
 *
 * Known simplification: these seed orders are real Order rows persisted to
 * Postgres, same as any user order (attributed to a fixed "market maker"
 * UUID). Restarting the app repeatedly will accumulate seed order rows in
 * the database — fine for a dev/demo exchange, but a production system would
 * either skip persistence for seed orders or clean them up on shutdown.
 */
@Component
class MarketSeeder(
    private val matchingEngine: MatchingEngine
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        SupportedPairs.ALL.forEach { pair ->
            val spread = pair.startingPrice.multiply(BigDecimal("0.001"))
            val bidPrice = pair.startingPrice.subtract(spread).setScale(8, RoundingMode.HALF_UP)
            val askPrice = pair.startingPrice.add(spread).setScale(8, RoundingMode.HALF_UP)

            matchingEngine.submit(
                Order(
                    userId = SupportedPairs.MARKET_MAKER_USER_ID,
                    tradingPair = pair.tradingPair,
                    side = OrderSide.BUY,
                    orderType = OrderType.LIMIT,
                    price = bidPrice,
                    quantity = BigDecimal("10")
                )
            )
            matchingEngine.submit(
                Order(
                    userId = SupportedPairs.MARKET_MAKER_USER_ID,
                    tradingPair = pair.tradingPair,
                    side = OrderSide.SELL,
                    orderType = OrderType.LIMIT,
                    price = askPrice,
                    quantity = BigDecimal("10")
                )
            )
        }
    }
}
