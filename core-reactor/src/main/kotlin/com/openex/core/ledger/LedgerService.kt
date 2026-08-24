package com.openex.core.ledger

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class LedgerService(
    private val ledgerEntryRepository: LedgerEntryRepository
) {

    fun balanceOf(accountId: UUID): BigDecimal =
        ledgerEntryRepository.sumAmountByAccountId(accountId)

    @Transactional
    fun recordMovement(entries: List<LedgerEntry>) {
        require(entries.isNotEmpty()) { "A movement must contain at least one entry" }
        val transactionIds = entries.map { it.transactionId }.toSet()
        require(transactionIds.size == 1) { "All entries in one movement must share a transactionId" }

        val totalCredit = entries.filter { it.direction == EntryDirection.CREDIT }
            .fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
        val totalDebit = entries.filter { it.direction == EntryDirection.DEBIT }
            .fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }

        require(totalCredit.compareTo(totalDebit) == 0) {
            "Double-entry violation: transaction ${transactionIds.first()} has " +
                "CREDIT=$totalCredit but DEBIT=$totalDebit, they must be equal"
        }

        ledgerEntryRepository.saveAll(entries)
    }

    @Transactional
    fun deposit(accountId: UUID, externalAccountId: UUID, amount: BigDecimal, reference: String?): UUID {
        require(amount > BigDecimal.ZERO) { "Deposit amount must be positive" }
        val transactionId = UUID.randomUUID()

        recordMovement(
            listOf(
                LedgerEntry(
                    transactionId = transactionId,
                    accountId = accountId,
                    amount = amount,
                    direction = EntryDirection.CREDIT,
                    entryType = "DEPOSIT",
                    reference = reference
                ),
                LedgerEntry(
                    transactionId = transactionId,
                    accountId = externalAccountId,
                    amount = amount,
                    direction = EntryDirection.DEBIT,
                    entryType = "DEPOSIT_SOURCE",
                    reference = reference
                )
            )
        )
        return transactionId
    }
}
