package io.github.drawjustin.kairos.auth.security

data class AuthenticatedUser(
    val id: Long,
    val email: String,
    val role: String,
)
