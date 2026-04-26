package io.github.drawjustin.kairos.context.repository

import io.github.drawjustin.kairos.context.entity.ProjectContextSource
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface ProjectContextSourceRepository : JpaRepository<ProjectContextSource, Long> {
    fun existsByProject_IdAndContextSource_IdAndDeletedAtIsNull(projectId: Long, contextSourceId: Long): Boolean

    fun findAllByProject_IdAndDeletedAtIsNullOrderByCreatedAtAsc(projectId: Long): List<ProjectContextSource>

    fun findByProject_IdAndContextSource_IdAndDeletedAtIsNull(
        projectId: Long,
        contextSourceId: Long,
    ): Optional<ProjectContextSource>
}
