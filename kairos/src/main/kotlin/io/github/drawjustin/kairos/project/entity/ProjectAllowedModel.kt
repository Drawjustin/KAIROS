package io.github.drawjustin.kairos.project.entity

import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.common.persistence.BaseEntity
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
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "project_allowed_model")
@SQLDelete(sql = "update project_allowed_model set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// project별로 호출 가능한 AI 모델을 제한하기 위한 정책 row다.
class ProjectAllowedModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    var model: AiModel,
) : BaseEntity()
