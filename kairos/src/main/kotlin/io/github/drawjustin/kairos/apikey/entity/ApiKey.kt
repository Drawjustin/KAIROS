package io.github.drawjustin.kairos.apikey.entity

import io.github.drawjustin.kairos.common.persistence.BaseEntity
import io.github.drawjustin.kairos.project.entity.Project
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
@Table(name = "api_key")
@SQLDelete(sql = "update api_key set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// API key 원문은 저장하지 않고 prefix와 hash만 남겨 이후 요청을 식별한다.
class ApiKey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    // API key는 project 소속으로 관리해 환경별 키 분리와 사용량 집계를 쉽게 한다.
    var project: Project,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdByUser: User,

    @Column(nullable = false, length = 120)
    var name: String,

    @Column(name = "key_prefix", nullable = false, length = 32)
    // 목록에서는 전체 키 대신 prefix만 보여줘 운영자가 어떤 키인지 식별하게 한다.
    var keyPrefix: String,

    @Column(name = "key_hash", nullable = false, length = 128)
    var keyHash: String,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
) : BaseEntity() {
    // 즉시 차단이 필요할 때 row를 지우지 않고도 인증만 막을 수 있다.
    fun revoke(now: Instant = Instant.now()) {
        revokedAt = now
    }
}
