package com.openex.core.matching

import com.openex.core.account.Account
import com.openex.core.account.AccountRepository
import com.openex.core.ledger.LedgerEntryRepository
import com.openex.core.ledger.LedgerService
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class OrderBalanceValidatorTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var ledgerEntryRepository: LedgerEntryRepository
    private lateinit var ledgerService: LedgerService
    private lateinit var validator: OrderBalanceValidator

    private val userId = UUID.randomUUID()
    private val btcAccountId = UUID.randomUUID()
    private val usdAccountId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        accountRepository = mock(AccountRepository::class.java)
        ledgerEntryRepository = mock(LedgerEntryRepository::class.java)
        ledgerService = LedgerService(ledgerEntryRepository)
        validator = OrderBalanceValidator(accountRepository, ledgerService)
    }

    private fun stubBalance(currency: String, accountId: UUID, balance: BigDecimal) {
        whenever(accountRepository.findByUserIdAndCurrency(userId, currency))
            .thenReturn(Account(id = accountId, userId = userId, currency = currency))
        whenever(ledgerEntryRepository.sumAmountByAccountId(accountId)).thenReturn(balance)
    }

    @Test
    fun `selling more than you hold is rejected`() {
        stubBalance("BTC", btcAccountId, BigDecimal("1"))

        assertThrows(InsufficientBalanceException::class.java) {
            validator.validate(userId, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, BigDecimal("100"), BigDecimal("2"))
        }
    }

    @Test
    fun `selling what you hold is allowed`() {
        stubBalance("BTC", btcAccountId, BigDecimal("5"))

        assertDoesNotThrow {
            validator.validate(userId, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, BigDecimal("100"), BigDecimal("2"))
        }
    }

    @Test
    fun `buying more than you can afford is rejected`() {
        stubBalance("USD", usdAccountId, BigDecimal("50"))

        assertThrows(InsufficientBalanceException::class.java) {
            // price 100 * quantity 2 = 200 needed, only have 50
            validator.validate(userId, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, BigDecimal("100"), BigDecimal("2"))
        }
    }

    @Test
    fun `buying what you can afford is allowed`() {
        stubBalance("USD", usdAccountId, BigDecimal("1000"))

        assertDoesNotThrow {
            validator.validate(userId, "BTC-USD", OrderSide.BUY, OrderType.LIMIT, BigDecimal("100"), BigDecimal("2"))
        }
    }

    @Test
    fun `selling with no wallet at all is rejected, not treated as zero-is-fine`() {
        whenever(accountRepository.findByUserIdAndCurrency(any<UUID>(), any<String>())).thenReturn(null)

        assertThrows(InsufficientBalanceException::class.java) {
            validator.validate(userId, "BTC-USD", OrderSide.SELL, OrderType.LIMIT, BigDecimal("100"), BigDecimal("1"))
        }
    }
}
