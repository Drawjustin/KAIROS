package io.github.drawjustin.kairos.auth.repository

import io.github.drawjustin.kairos.auth.domain.RefreshSession
import org.springframework.data.jpa.repository.JpaRepository

// refresh 세션 조회와 대량 폐기에 필요한 쿼리만 노출한다.
interface RefreshSessionRepository : JpaRepository<RefreshSession, Long> {
    // refresh JWT의 sid claim으로 서버 측 세션을 찾는다.
    fun findBySessionIdAndDeletedAtIsNullAndUser_DeletedAtIsNull(sessionId: String): RefreshSession?

    // 재사용 탐지 시 사용자에게 남아 있는 활성 세션을 한 번에 찾는다.
    fun findAllByUser_IdAndUser_DeletedAtIsNullAndRevokedAtIsNullAndDeletedAtIsNull(userId: Long): List<RefreshSession>
}
