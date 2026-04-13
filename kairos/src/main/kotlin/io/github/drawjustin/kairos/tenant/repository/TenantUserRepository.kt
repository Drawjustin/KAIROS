package io.github.drawjustin.kairos.tenant.repository

import io.github.drawjustin.kairos.tenant.entity.TenantUser
import io.github.drawjustin.kairos.tenant.entity.TenantUserRole
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface TenantUserRepository : JpaRepository<TenantUser, Long> {
    fun existsByTenant_IdAndUser_IdAndRoleInAndDeletedAtIsNull(
        tenantId: Long,
        userId: Long,
        roles: Collection<TenantUserRole>,
    ): Boolean

    fun existsByTenant_IdAndUser_IdAndDeletedAtIsNull(
        tenantId: Long,
        userId: Long,
    ): Boolean

    fun findAllByTenant_IdAndDeletedAtIsNullOrderByCreatedAtAsc(tenantId: Long): List<TenantUser>

    fun findByIdAndDeletedAtIsNull(id: Long): Optional<TenantUser>

    fun countByTenant_IdAndRoleAndDeletedAtIsNull(
        tenantId: Long,
        role: TenantUserRole,
    ): Long
}
