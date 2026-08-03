package com.openex.core.ledger

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "idempotency_keys")
class IdempotencyKey(
    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    val idempotencyKey: String,

    @Column(name = "response_body")
    val responseBody: String,

    @Column(name = "status_code")
    val statusCode: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
