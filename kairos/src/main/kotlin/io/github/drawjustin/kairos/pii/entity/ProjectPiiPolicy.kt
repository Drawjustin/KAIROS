package io.github.drawjustin.kairos.pii.entity

import io.github.drawjustin.kairos.common.persistence.BaseEntity
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiType
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
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "project_pii_policy")
@SQLDelete(sql = "update project_pii_policy set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// project가 기본 정책과 다르게 운영하려는 민감정보 타입만 row로 남긴다.
class ProjectPiiPolicy(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @Enumerated(EnumType.STRING)
    @Column(name = "pii_type", nullable = false, length = 60)
    var piiType: PiiType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var action: PiiAction,
) : BaseEntity()
