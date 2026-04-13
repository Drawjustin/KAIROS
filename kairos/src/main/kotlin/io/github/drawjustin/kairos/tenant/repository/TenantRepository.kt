package io.github.drawjustin.kairos.tenant.repository

import io.github.drawjustin.kairos.tenant.entity.Tenant
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<Tenant, Long> {
    fun existsByNameIgnoreCaseAndDeletedAtIsNull(name: String): Boolean

    fun findAllByOwnerUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(ownerUserId: Long): List<Tenant>

    fun findByIdAndDeletedAtIsNull(id: Long): Optional<Tenant>
}
