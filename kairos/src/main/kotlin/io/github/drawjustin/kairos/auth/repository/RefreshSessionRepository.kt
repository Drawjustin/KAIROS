package io.github.drawjustin.kairos.auth.repository

import io.github.drawjustin.kairos.auth.domain.RefreshSession
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshSessionRepository : JpaRepository<RefreshSession, Long> {
    fun findBySessionId(sessionId: String): RefreshSession?

    fun findAllByUser_IdAndRevokedAtIsNull(userId: Long): List<RefreshSession>
}
