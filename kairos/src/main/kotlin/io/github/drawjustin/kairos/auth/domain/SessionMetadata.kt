package io.github.drawjustin.kairos.auth.domain

// refresh 세션 저장 시 같이 남겨둘 클라이언트 환경 정보다.
data class SessionMetadata(
    val platform: String?,
    val device: String?,
    val ipAddress: String?,
    val userAgent: String?,
)
