package io.github.drawjustin.kairos.ai.entity

import io.github.drawjustin.kairos.apikey.entity.ApiKey
import io.github.drawjustin.kairos.ai.type.AiUsageStatus
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
@Table(name = "ai_usage_log")
@EntityListeners(AuditingEntityListener::class)
// AI 호출 원장은 비용/감사 기준이므로 수정/삭제 흐름 없이 append-only로 남긴다.
class AiUsageLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_key_id", nullable = false)
    var apiKey: ApiKey,

    @Column(nullable = false, length = 50)
    // enum이 바뀌어도 과거 로그 해석이 깨지지 않게 provider 이름을 스냅샷으로 저장한다.
    var provider: String,

    @Column(nullable = false, length = 120)
    // 모델 ID도 호출 시점의 원문 값을 남겨 deprecated 모델 사용량까지 추적한다.
    var model: String,

    @Column(name = "input_tokens", nullable = false)
    var inputTokens: Int = 0,

    @Column(name = "output_tokens", nullable = false)
    var outputTokens: Int = 0,

    @Column(name = "total_tokens", nullable = false)
    var totalTokens: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: AiUsageStatus,

    @Column(name = "latency_ms", nullable = false)
    var latencyMs: Long,

    @Column(name = "error_code", length = 50)
    var errorCode: String? = null,

    @Column(name = "provider_response_id", length = 160)
    var providerResponseId: String? = null,

    @Column(name = "trace_id", length = 64)
    var traceId: String? = null,
) {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
