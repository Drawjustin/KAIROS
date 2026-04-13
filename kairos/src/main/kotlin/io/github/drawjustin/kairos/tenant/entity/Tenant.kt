package io.github.drawjustin.kairos.tenant.entity

import io.github.drawjustin.kairos.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "tenant")
@SQLDelete(sql = "update tenant set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// 사용량, 예산, 정책을 묶는 상위 운영 단위다.
class Tenant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 120)
    // tenant 이름은 유니크하게 관리한다.
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: TenantStatus = TenantStatus.ACTIVE,
) : BaseEntity()
