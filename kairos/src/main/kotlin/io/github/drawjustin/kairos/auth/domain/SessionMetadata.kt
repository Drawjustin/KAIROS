package io.github.drawjustin.kairos.auth.domain

data class SessionMetadata(
    val platform: String?,
    val device: String?,
    val ipAddress: String?,
    val userAgent: String?,
)
