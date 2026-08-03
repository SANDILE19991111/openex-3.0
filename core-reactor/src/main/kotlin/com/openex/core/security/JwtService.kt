package com.openex.core.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${openex.jwt.secret}") secretKeyString: String,
    @Value("\${openex.jwt.expiration-minutes:60}") private val expirationMinutes: Long
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(secretKeyString.toByteArray())

    fun generateToken(userId: UUID, username: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationMinutes * 60 * 1000)
        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact()
    }

    /** Returns the userId (subject) if the token is valid, null otherwise. */
    fun validateAndGetUserId(token: String): UUID? {
        return try {
            val claims = Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token).payload
            UUID.fromString(claims.subject)
        } catch (ex: Exception) {
            null
        }
    }
}
