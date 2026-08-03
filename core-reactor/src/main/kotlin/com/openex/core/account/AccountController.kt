package com.openex.core.account

import com.openex.core.ledger.LedgerService
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateAccountRequest(
    @field:NotNull val userId: UUID,
    @field:NotBlank val currency: String
)

data class AccountResponse(
    val id: UUID,
    val userId: UUID,
    val currency: String,
    val balance: java.math.BigDecimal
)

@RestController
@RequestMapping("/api/wallets")
class AccountController(
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService
) {

    @PostMapping
    fun createAccount(@RequestBody req: CreateAccountRequest): ResponseEntity<AccountResponse> {
        val existing = accountRepository.findByUserIdAndCurrency(req.userId, req.currency)
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
        val saved = accountRepository.save(Account(userId = req.userId, currency = req.currency))
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AccountResponse(saved.id, saved.userId, saved.currency, java.math.BigDecimal.ZERO))
    }

    @GetMapping("/{id}")
    fun getAccount(@PathVariable id: UUID): ResponseEntity<AccountResponse> {
        val account = accountRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val balance = ledgerService.balanceOf(account.id)
        return ResponseEntity.ok(AccountResponse(account.id, account.userId, account.currency, balance))
    }

    @GetMapping
    fun getAccountsForUser(@RequestParam userId: UUID): ResponseEntity<List<AccountResponse>> {
        val accounts = accountRepository.findAllByUserId(userId).map {
            AccountResponse(it.id, it.userId, it.currency, ledgerService.balanceOf(it.id))
        }
        return ResponseEntity.ok(accounts)
    }
}
