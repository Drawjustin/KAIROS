package io.github.drawjustin.kairos.pii.repository

import io.github.drawjustin.kairos.pii.entity.ProjectPiiPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface ProjectPiiPolicyRepository : JpaRepository<ProjectPiiPolicy, Long> {
    fun findAllByProject_IdAndDeletedAtIsNull(projectId: Long): List<ProjectPiiPolicy>
}
