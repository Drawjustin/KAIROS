package io.github.drawjustin.kairos.tenant.entity

import io.github.drawjustin.kairos.common.persistence.BaseEntity
import io.github.drawjustin.kairos.user.entity.User
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
@Table(name = "tenant_user")
@SQLDelete(sql = "update tenant_user set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// tenant와 user의 소속 관계와 역할을 함께 표현하는 조인 엔티티다.
class TenantUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    var tenant: Tenant,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var role: TenantUserRole,
) : BaseEntity()
