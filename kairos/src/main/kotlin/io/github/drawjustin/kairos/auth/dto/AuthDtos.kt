package io.github.drawjustin.kairos.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class RegisterRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 100)
    val password: String,
)

data class LoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String,
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class LogoutRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val sessionId: String,
)

data class MeResponse(
    val id: Long,
    val email: String,
    val role: String,
)
