package com.openex.core.ledger

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class EntryDirection { CREDIT, DEBIT }

@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "transaction_id", nullable = false)
    val transactionId: UUID,

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    // Always positive. Whether it increases or decreases the balance is
    // determined entirely by `direction`, never by the sign of amount.
    @Column(nullable = false, precision = 18, scale = 8)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val direction: EntryDirection,

    @Column(name = "entry_type", nullable = false)
    val entryType: String = "GENERAL",

    @Column(name = "reference")
    val reference: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
