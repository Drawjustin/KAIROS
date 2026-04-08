package io.github.drawjustin.kairos.auth.domain

import io.github.drawjustin.kairos.common.persistence.BaseEntity
import io.github.drawjustin.kairos.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "refresh_sessions")
@SQLDelete(sql = "update refresh_sessions set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// 각 refresh token의 서버 측 상태를 관리하는 엔티티다.
class RefreshSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "session_id", nullable = false, unique = true, length = 100)
    // JWT 안의 sid claim과 매칭되는 서버 측 세션 식별자다.
    val sessionId: String,

    @Column(name = "token_hash", nullable = false, length = 128)
    // 원문 refresh token 대신 해시만 저장해 DB 유출 시 피해를 줄인다.
    var tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    @Column(name = "platform")
    var platform: String? = null,

    @Column(name = "device")
    var device: String? = null,

    @Column(name = "ip_address")
    var ipAddress: String? = null,

    @Column(name = "user_agent", length = 1000)
    var userAgent: String? = null,
) : BaseEntity() {
    // 로그아웃, rotation, 재사용 탐지 시 세션을 폐기 상태로 바꾼다.
    fun revoke(now: Instant = Instant.now()) {
        revokedAt = now
    }

    // 정상적으로 사용된 마지막 시각을 남겨 추적 가능하게 한다.
    fun markUsed(now: Instant = Instant.now()) {
        lastUsedAt = now
    }
}
