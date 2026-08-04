package com.openex.core.matching

import java.math.BigDecimal
import java.util.UUID

/**
 * The fixed set of trading pairs this exchange supports, with a rough
 * starting price for each. Used to seed the order book on startup so the
 * Markets page has something to show before any real user has traded.
 */
object SupportedPairs {

    // Fixed system UUID that owns the seeded market-maker orders. Not a real
    // user — orders.user_id has no FK constraint, so any UUID is valid here.
    val MARKET_MAKER_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000f0")

    data class SeedPair(val tradingPair: String, val displayName: String, val startingPrice: BigDecimal)

    val ALL: List<SeedPair> = listOf(
        SeedPair("BTC-USD", "Bitcoin", BigDecimal("65000.00")),
        SeedPair("ETH-USD", "Ethereum", BigDecimal("3400.00")),
        SeedPair("SOL-USD", "Solana", BigDecimal("165.00")),
        SeedPair("XRP-USD", "XRP", BigDecimal("0.62")),
        SeedPair("ADA-USD", "Cardano", BigDecimal("0.45")),
        SeedPair("DOGE-USD", "Dogecoin", BigDecimal("0.14")),
        SeedPair("MATIC-USD", "Polygon", BigDecimal("0.78")),
        SeedPair("DOT-USD", "Polkadot", BigDecimal("6.80")),
        SeedPair("LTC-USD", "Litecoin", BigDecimal("85.00")),
        SeedPair("AVAX-USD", "Avalanche", BigDecimal("36.50"))
    )
}
