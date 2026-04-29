package io.github.drawjustin.kairos.context.entity

import io.github.drawjustin.kairos.context.type.ContextSearchPurpose
import io.github.drawjustin.kairos.context.type.ContextSearchStatus
import io.github.drawjustin.kairos.project.entity.Project
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@Table(name = "context_search_log")
@EntityListeners(AuditingEntityListener::class)
// 개발 AI 도구가 어떤 project/source/query로 사내 참조자료를 조회했는지 append-only로 남긴다.
class ContextSearchLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val purpose: ContextSearchPurpose,

    @Column(nullable = false, length = 2000)
    val query: String,

    @Column(name = "requested_context_source_ids", length = 1000)
    val requestedContextSourceIds: String? = null,

    @Column(name = "searched_context_source_ids", length = 1000)
    val searchedContextSourceIds: String? = null,

    @Column(name = "result_count", nullable = false)
    val resultCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val status: ContextSearchStatus,

    @Column(name = "latency_ms", nullable = false)
    val latencyMs: Long,

    @Column(name = "error_code", length = 50)
    val errorCode: String? = null,

    @Column(name = "trace_id", length = 64)
    val traceId: String? = null,
) {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
