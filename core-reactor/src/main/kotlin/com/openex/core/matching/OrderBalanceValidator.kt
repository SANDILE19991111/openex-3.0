package com.openex.core.matching

import com.openex.core.account.AccountRepository
import com.openex.core.ledger.LedgerService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

class InsufficientBalanceException(message: String) : RuntimeException(message)

/**
 * Checks a user actually has enough of the relevant currency before an
 * order is allowed to reach the matching engine. Without this, a SELL
 * order settles even when the seller doesn't hold the asset, driving their
 * balance negative - which is what happens on a real exchange only if
 * margin/short-selling is explicitly offered, never by accident.
 *
 * SELL orders (any type): must hold at least `quantity` of the base
 * currency (e.g. BTC in BTC-USD).
 *
 * BUY LIMIT orders: must hold at least `price * quantity` of the quote
 * currency (e.g. USD in BTC-USD) - the worst case, since a limit buy never
 * fills above its limit price.
 *
 * BUY MARKET orders: the fill price isn't known ahead of time, so this is
 * a known simplification - we don't block market buys on balance. A real
 * exchange would reserve funds against the current best ask; that's a
 * reasonable next step but out of scope here.
 */
@Service
class OrderBalanceValidator(
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService
) {

    fun validate(userId: UUID, tradingPair: String, side: OrderSide, orderType: OrderType, price: BigDecimal?, quantity: BigDecimal) {
        val parts = tradingPair.split("-")
        require(parts.size == 2) { "Unexpected trading pair format: $tradingPair" }
        val (baseCurrency, quoteCurrency) = parts

        when (side) {
            OrderSide.SELL -> {
                val available = balanceOf(userId, baseCurrency)
                if (available < quantity) {
                    throw InsufficientBalanceException(
                        "Insufficient $baseCurrency balance: have $available, need $quantity"
                    )
                }
            }
            OrderSide.BUY -> {
                if (orderType == OrderType.LIMIT && price != null) {
                    val required = price.multiply(quantity)
                    val available = balanceOf(userId, quoteCurrency)
                    if (available < required) {
                        throw InsufficientBalanceException(
                            "Insufficient $quoteCurrency balance: have $available, need $required"
                        )
                    }
                }
                // MARKET buys: not validated here - see class doc.
            }
        }
    }

    private fun balanceOf(userId: UUID, currency: String): BigDecimal {
        val account = accountRepository.findByUserIdAndCurrency(userId, currency) ?: return BigDecimal.ZERO
        return ledgerService.balanceOf(account.id)
    }
}
