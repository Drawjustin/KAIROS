package io.github.drawjustin.kairos.context.entity

import io.github.drawjustin.kairos.common.persistence.BaseEntity
import io.github.drawjustin.kairos.project.entity.Project
import jakarta.persistence.Entity
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
@Table(name = "project_context_source")
@SQLDelete(sql = "update project_context_source set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// 어떤 project가 어떤 context source를 사용할 수 있는지 연결한다.
class ProjectContextSource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "context_source_id", nullable = false)
    var contextSource: ContextSource,
) : BaseEntity()
