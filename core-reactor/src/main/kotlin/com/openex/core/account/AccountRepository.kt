package com.openex.core.account

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByUserIdAndCurrency(userId: UUID, currency: String): Account?
    fun findAllByUserId(userId: UUID): List<Account>
}
