package com.openex.core.ledger

import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, String>
