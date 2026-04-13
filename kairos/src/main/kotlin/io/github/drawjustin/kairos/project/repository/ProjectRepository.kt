package io.github.drawjustin.kairos.project.repository

import io.github.drawjustin.kairos.project.entity.Project
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface ProjectRepository : JpaRepository<Project, Long> {
    fun existsByTenant_IdAndNameIgnoreCaseAndDeletedAtIsNull(tenantId: Long, name: String): Boolean

    fun findAllByTenant_IdAndDeletedAtIsNullOrderByCreatedAtAsc(tenantId: Long): List<Project>

    fun findByIdAndDeletedAtIsNull(id: Long): Optional<Project>
}
