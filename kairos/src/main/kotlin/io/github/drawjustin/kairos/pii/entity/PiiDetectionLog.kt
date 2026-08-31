package io.github.drawjustin.kairos.pii.entity

import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiInspectionSource
import io.github.drawjustin.kairos.pii.type.PiiType
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
@Table(name = "pii_detection_log")
@EntityListeners(AuditingEntityListener::class)
// 어떤 타입이 몇 건 검출되어 어떻게 처리됐는지만 append-only로 남긴다.
// 검출된 값은 원문도 일부 조각도 저장하지 않는다. 감사 로그가 유출 경로가 되면 통제의 의미가 없다.
// 요청 단위 추적이 필요하면 traceId로 ai_usage_log, context_search_log와 이어 붙인다.
class PiiDetectionLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val source: PiiInspectionSource,

    @Enumerated(EnumType.STRING)
    @Column(name = "pii_type", nullable = false, length = 60)
    val piiType: PiiType,

    @Column(name = "detected_count", nullable = false)
    val detectedCount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val action: PiiAction,

    @Column(name = "trace_id", length = 64)
    val traceId: String? = null,
) {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
