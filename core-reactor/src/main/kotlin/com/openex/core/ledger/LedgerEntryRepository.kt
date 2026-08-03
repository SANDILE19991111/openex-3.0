package com.openex.core.ledger

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.UUID

interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {

    // Balance is always derived: CREDITs add, DEBITs subtract. Never stored
    // as a mutable column — this is what makes the ledger tamper-evident.
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN e.direction = com.openex.core.ledger.EntryDirection.CREDIT
                 THEN e.amount ELSE -e.amount END
        ), 0)
        FROM LedgerEntry e
        WHERE e.accountId = :accountId
        """
    )
    fun sumAmountByAccountId(@Param("accountId") accountId: UUID): BigDecimal

    fun findAllByTransactionId(transactionId: UUID): List<LedgerEntry>
}
