package io.github.drawjustin.kairos.tenant.repository

import io.github.drawjustin.kairos.tenant.entity.TenantUser
import io.github.drawjustin.kairos.tenant.entity.TenantUserRole
import org.springframework.data.jpa.repository.JpaRepository

interface TenantUserRepository : JpaRepository<TenantUser, Long> {
    fun existsByTenant_IdAndUser_IdAndRoleInAndDeletedAtIsNull(
        tenantId: Long,
        userId: Long,
        roles: Collection<TenantUserRole>,
    ): Boolean
}
