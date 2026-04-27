package io.github.drawjustin.kairos.project.repository

import io.github.drawjustin.kairos.project.entity.Project
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProjectRepository : JpaRepository<Project, Long> {
    fun existsByTenant_IdAndNameIgnoreCaseAndDeletedAtIsNull(tenantId: Long, name: String): Boolean

    fun findAllByTenant_IdAndDeletedAtIsNullOrderByCreatedAtAsc(tenantId: Long): List<Project>

    fun findAllByDeletedAtIsNullOrderByCreatedAtAsc(): List<Project>

    @Query(
        """
        select distinct p
        from Project p
        join p.tenant t
        join TenantUser tu on tu.tenant = t
        where tu.user.id = :userId
          and tu.deletedAt is null
          and p.deletedAt is null
        order by p.createdAt asc
        """,
    )
    fun findAccessibleProjectsByUserId(@Param("userId") userId: Long): List<Project>

    fun findByIdAndDeletedAtIsNull(id: Long): Optional<Project>
}
