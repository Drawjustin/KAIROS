package io.github.drawjustin.kairos.auth.domain

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

@Entity
@Table(name = "refresh_sessions")
class RefreshSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "session_id", nullable = false, unique = true, length = 100)
    val sessionId: String,

    @Column(name = "token_hash", nullable = false, length = 128)
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

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun revoke(now: Instant = Instant.now()) {
        revokedAt = now
        updatedAt = now
    }

    fun markUsed(now: Instant = Instant.now()) {
        lastUsedAt = now
        updatedAt = now
    }
}
