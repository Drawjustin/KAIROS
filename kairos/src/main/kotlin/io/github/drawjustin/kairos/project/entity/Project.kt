package io.github.drawjustin.kairos.project.entity

import io.github.drawjustin.kairos.common.persistence.BaseEntity
import io.github.drawjustin.kairos.project.type.ProjectEnvironment
import io.github.drawjustin.kairos.project.type.ProjectStatus
import io.github.drawjustin.kairos.tenant.entity.Tenant
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
@Table(name = "project")
@SQLDelete(sql = "update project set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// tenant 아래에서 실제 서비스/환경을 구분하는 운영 단위다.
class Project(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    // project는 반드시 하나의 tenant에 속해 운영/과금 경계를 공유한다.
    var tenant: Tenant,

    @Column(nullable = false, length = 120)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var environment: ProjectEnvironment = ProjectEnvironment.OPER,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: ProjectStatus = ProjectStatus.ACTIVE,
) : BaseEntity()
