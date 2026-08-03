package com.openex.core.ledger

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

// A single well-known "external world" account per asset. In a real system
// you'd model this per asset_code; kept simple here for the Week 1 skeleton.
private val EXTERNAL_SOURCE_ACCOUNT_ID: UUID =
    UUID.fromString("00000000-0000-0000-0000-000000000001")

data class DepositRequest(
    @field:NotNull val accountId: UUID,
    @field:NotNull @field:Positive val amount: BigDecimal,
    val reference: String? = null
)

data class DepositResponse(
    val transactionId: UUID,
    val accountId: UUID,
    val newBalance: BigDecimal
)

@RestController
@RequestMapping("/api/wallets")
class LedgerController(
    private val ledgerService: LedgerService,
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val objectMapper: ObjectMapper
) {

    /**
     * "Faucet" endpoint for simulated funds. Protected by an Idempotency-Key
     * header: replaying the same key returns the original response instead
     * of creating a second deposit — this is the "frantic trader mashes Buy
     * 47 times" guardrail.
     */
    @PostMapping("/deposit")
    fun deposit(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody req: DepositRequest
    ): ResponseEntity<String> {
        val existingKey = idempotencyKeyRepository.findById(idempotencyKey)
        if (existingKey.isPresent) {
            val existing = existingKey.get()
            return ResponseEntity.status(existing.statusCode).body(existing.responseBody)
        }

        val transactionId = ledgerService.deposit(
            accountId = req.accountId,
            externalAccountId = EXTERNAL_SOURCE_ACCOUNT_ID,
            amount = req.amount,
            reference = req.reference
        )
        val newBalance = ledgerService.balanceOf(req.accountId)
        val body = DepositResponse(transactionId, req.accountId, newBalance)
        val bodyJson = objectMapper.writeValueAsString(body)

        idempotencyKeyRepository.save(
            IdempotencyKey(
                idempotencyKey = idempotencyKey,
                responseBody = bodyJson,
                statusCode = HttpStatus.CREATED.value()
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(bodyJson)
    }
}
