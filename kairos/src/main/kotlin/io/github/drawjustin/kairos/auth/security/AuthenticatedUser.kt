package io.github.drawjustin.kairos.auth.security

import io.github.drawjustin.kairos.user.entity.UserRole

// JWT claim을 읽기 쉬운 형태로 SecurityContext에 올려둔 principal이다.
data class AuthenticatedUser(
    val id: Long,
    val email: String,
    val role: UserRole,
)
