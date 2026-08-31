package io.github.drawjustin.kairos.budget.entity

import io.github.drawjustin.kairos.budget.type.BudgetPeriod
import io.github.drawjustin.kairos.common.persistence.BaseEntity
import io.github.drawjustin.kairos.project.entity.Project
import jakarta.persistence.Column
import jakarta.persistence.Entity
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
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "project_budget")
@SQLDelete(sql = "update project_budget set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// project의 기간별 호출/토큰 한도와 현재 소진량이다.
// consumed 값은 엔티티를 통해 수정하지 않는다. 동시 요청에서 값이 어긋나므로
// 반드시 ProjectBudgetRepository의 조건부 UPDATE로만 변경한다.
class ProjectBudget(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var period: BudgetPeriod,

    @Column(name = "request_limit")
    // null이면 호출 횟수는 제한하지 않는다.
    var requestLimit: Long? = null,

    @Column(name = "token_limit")
    // null이면 토큰 사용량은 제한하지 않는다.
    var tokenLimit: Long? = null,

    @Column(name = "consumed_requests", nullable = false)
    var consumedRequests: Long = 0,

    @Column(name = "consumed_tokens", nullable = false)
    var consumedTokens: Long = 0,

    @Column(name = "period_started_at", nullable = false)
    var periodStartedAt: Instant,
) : BaseEntity()
