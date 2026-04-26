package io.github.drawjustin.kairos.project.repository

import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.project.entity.ProjectAllowedModel
import org.springframework.data.jpa.repository.JpaRepository

interface ProjectAllowedModelRepository : JpaRepository<ProjectAllowedModel, Long> {
    fun existsByProject_IdAndModelAndDeletedAtIsNull(projectId: Long, model: AiModel): Boolean

    fun findAllByProject_IdAndDeletedAtIsNullOrderByModelAsc(projectId: Long): List<ProjectAllowedModel>
}
