package com.openex.core.user

import com.openex.core.security.JwtService
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank val username: String,
    @field:NotBlank @field:Size(min = 8) val password: String
)

data class LoginRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String
)

data class AuthResponse(
    val token: String,
    val userId: UUID,
    val username: String
)

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    @PostMapping("/register")
    fun register(@RequestBody req: RegisterRequest): ResponseEntity<AuthResponse> {
        if (userRepository.findByUsername(req.username) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
        val user = userRepository.save(
            User(username = req.username, passwordHash = passwordEncoder.encode(req.password))
        )
        val token = jwtService.generateToken(user.id, user.username)
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse(token, user.id, user.username))
    }

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): ResponseEntity<AuthResponse> {
        val user = userRepository.findByUsername(req.username)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!passwordEncoder.matches(req.password, user.passwordHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val token = jwtService.generateToken(user.id, user.username)
        return ResponseEntity.ok(AuthResponse(token, user.id, user.username))
    }
}
