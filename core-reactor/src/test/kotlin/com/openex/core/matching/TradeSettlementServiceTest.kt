package com.openex.core.matching

import com.openex.core.account.Account
import com.openex.core.account.AccountRepository
import com.openex.core.ledger.LedgerEntryRepository
import com.openex.core.ledger.LedgerService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class TradeSettlementServiceTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var ledgerEntryRepository: LedgerEntryRepository
    private lateinit var ledgerService: LedgerService
    private lateinit var settlementService: TradeSettlementService

    private val buyerId = UUID.randomUUID()
    private val sellerId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        orderRepository = mock(OrderRepository::class.java)
        accountRepository = mock(AccountRepository::class.java)
        ledgerEntryRepository = mock(LedgerEntryRepository::class.java)
        ledgerService = LedgerService(ledgerEntryRepository)
        settlementService = TradeSettlementService(ledgerService, accountRepository, orderRepository)

        whenever(ledgerEntryRepository.saveAll(any<List<com.openex.core.ledger.LedgerEntry>>()))
            .thenAnswer { it.arguments[0] }

        // Auto-create semantics: no existing account, so save() just returns what it was given.
        whenever(accountRepository.findByUserIdAndCurrency(any<UUID>(), any<String>())).thenReturn(null)
        whenever(accountRepository.save(any<Account>())).thenAnswer { it.arguments[0] }
    }

    private fun buyOrder(userId: UUID = buyerId) = Order(
        userId = userId, tradingPair = "BTC-USD", side = OrderSide.BUY,
        orderType = OrderType.LIMIT, price = BigDecimal("100.00"), quantity = BigDecimal("5")
    )

    private fun sellOrder(userId: UUID = sellerId) = Order(
        userId = userId, tradingPair = "BTC-USD", side = OrderSide.SELL,
        orderType = OrderType.LIMIT, price = BigDecimal("100.00"), quantity = BigDecimal("5")
    )

    @Test
    fun `settling a trade moves quote currency from buyer to seller and base currency from seller to buyer`() {
        val buy = buyOrder()
        val sell = sellOrder()
        whenever(orderRepository.findById(buy.id)).thenReturn(Optional.of(buy))
        whenever(orderRepository.findById(sell.id)).thenReturn(Optional.of(sell))

        val trade = Trade(
            tradingPair = "BTC-USD",
            buyOrderId = buy.id,
            sellOrderId = sell.id,
            price = BigDecimal("100.00"),
            quantity = BigDecimal("3")
        )

        settlementService.settle(trade)

        // 4 accounts should have been touched: buyer-USD, buyer-BTC, seller-USD, seller-BTC.
        val savedAccounts = mutableListOf<Account>()
        org.mockito.kotlin.verify(accountRepository, org.mockito.kotlin.times(4)).save(
            org.mockito.kotlin.check { savedAccounts.add(it) }
        )
        val currencies = savedAccounts.map { it.userId to it.currency }.toSet()
        assertEquals(
            setOf(buyerId to "USD", buyerId to "BTC", sellerId to "USD", sellerId to "BTC"),
            currencies
        )
    }

    @Test
    fun `settlement amounts are price times quantity for quote currency and quantity for base currency`() {
        val buy = buyOrder()
        val sell = sellOrder()
        whenever(orderRepository.findById(buy.id)).thenReturn(Optional.of(buy))
        whenever(orderRepository.findById(sell.id)).thenReturn(Optional.of(sell))

        val trade = Trade(
            tradingPair = "BTC-USD",
            buyOrderId = buy.id,
            sellOrderId = sell.id,
            price = BigDecimal("200.00"),
            quantity = BigDecimal("2")
        )

        val capturedEntries = mutableListOf<List<com.openex.core.ledger.LedgerEntry>>()
        whenever(ledgerEntryRepository.saveAll(any<List<com.openex.core.ledger.LedgerEntry>>()))
            .thenAnswer {
                @Suppress("UNCHECKED_CAST")
                val entries = it.arguments[0] as List<com.openex.core.ledger.LedgerEntry>
                capturedEntries.add(entries)
                entries
            }

        settlementService.settle(trade)

        val entries = capturedEntries.single()
        assertEquals(4, entries.size)

        val quoteEntries = entries.filter { it.amount.compareTo(BigDecimal("400.00")) == 0 }
        val baseEntries = entries.filter { it.amount.compareTo(BigDecimal("2")) == 0 }
        assertEquals(2, quoteEntries.size, "buyer debit + seller credit of 200*2=400 USD")
        assertEquals(2, baseEntries.size, "seller debit + buyer credit of 2 BTC")

        // Every movement must still balance: total CREDIT == total DEBIT.
        val totalCredit = entries.filter { it.direction == com.openex.core.ledger.EntryDirection.CREDIT }
            .sumOf { it.amount }
        val totalDebit = entries.filter { it.direction == com.openex.core.ledger.EntryDirection.DEBIT }
            .sumOf { it.amount }
        assertEquals(0, totalCredit.compareTo(totalDebit))
    }
}
