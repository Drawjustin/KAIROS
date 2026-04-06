package io.github.drawjustin.kairos.auth.repository

import io.github.drawjustin.kairos.auth.domain.RefreshSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RefreshSessionRepository : JpaRepository<RefreshSession, Long> {
    fun findBySessionId(sessionId: String): RefreshSession?

    @Query(
        """
        select rs
        from RefreshSession rs
        where rs.user.id = :userId
          and rs.revokedAt is null
        """
    )
    fun findActiveSessionsByUserId(userId: Long): List<RefreshSession>
}
