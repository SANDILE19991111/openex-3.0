package com.openex.core.matching

import com.openex.core.account.Account
import com.openex.core.account.AccountRepository
import com.openex.core.ledger.EntryDirection
import com.openex.core.ledger.LedgerEntry
import com.openex.core.ledger.LedgerService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode
import java.util.UUID

/**
 * Settles an executed trade into the double-entry ledger - the piece that
 * was missing before: trades were being matched and recorded, but no money
 * ever actually moved between the buyer's and seller's wallets.
 *
 * For a trade on "BTC-USD":
 *  - buyer pays quote currency (USD) = price * quantity, receives base (BTC) = quantity
 *  - seller receives quote currency (USD) = price * quantity, pays base (BTC) = quantity
 *
 * Wallets are auto-created if a user doesn't already have one in the needed
 * currency - a trader shouldn't have their trade silently fail to settle
 * just because they never manually created a BTC wallet before their first
 * BTC trade.
 */
@Service
class TradeSettlementService(
    private val ledgerService: LedgerService,
    private val accountRepository: AccountRepository,
    private val orderRepository: OrderRepository
) {

    @Transactional
    fun settle(trade: Trade) {
        val buyOrder = orderRepository.findById(trade.buyOrderId)
            .orElseThrow { IllegalStateException("Buy order ${trade.buyOrderId} not found for trade ${trade.id}") }
        val sellOrder = orderRepository.findById(trade.sellOrderId)
            .orElseThrow { IllegalStateException("Sell order ${trade.sellOrderId} not found for trade ${trade.id}") }

        val parts = trade.tradingPair.split("-")
        require(parts.size == 2) { "Unexpected trading pair format: ${trade.tradingPair}" }
        val (baseCurrency, quoteCurrency) = parts

        val quoteAmount = trade.price.multiply(trade.quantity).setScale(8, RoundingMode.HALF_UP)
        val baseAmount = trade.quantity.setScale(8, RoundingMode.HALF_UP)

        val buyerQuoteAccount = getOrCreateAccount(buyOrder.userId, quoteCurrency)
        val buyerBaseAccount = getOrCreateAccount(buyOrder.userId, baseCurrency)
        val sellerQuoteAccount = getOrCreateAccount(sellOrder.userId, quoteCurrency)
        val sellerBaseAccount = getOrCreateAccount(sellOrder.userId, baseCurrency)

        val transactionId = UUID.randomUUID()
        val reference = "trade:${trade.id}"

        ledgerService.recordMovement(
            listOf(
                LedgerEntry(
                    transactionId = transactionId,
                    accountId = buyerQuoteAccount.id,
                    amount = quoteAmount,
                    direction = EntryDirection.DEBIT,
                    entryType = "TRADE_FILL",
                    reference = reference
                ),
                LedgerEntry(
                    transactionId = transactionId,
                    accountId = sellerQuoteAccount.id,
                    amount = quoteAmount,
                    direction = EntryDirection.CREDIT,
                    entryType = "TRADE_FILL",
                    reference = reference
                ),
                LedgerEntry(
                    transactionId = transactionId,
                    accountId = sellerBaseAccount.id,
                    amount = baseAmount,
                    direction = EntryDirection.DEBIT,
                    entryType = "TRADE_FILL",
                    reference = reference
                ),
                LedgerEntry(
                    transactionId = transactionId,
                    accountId = buyerBaseAccount.id,
                    amount = baseAmount,
                    direction = EntryDirection.CREDIT,
                    entryType = "TRADE_FILL",
                    reference = reference
                )
            )
        )
    }

    private fun getOrCreateAccount(userId: UUID, currency: String): Account =
        accountRepository.findByUserIdAndCurrency(userId, currency)
            ?: accountRepository.save(Account(userId = userId, currency = currency))
}
