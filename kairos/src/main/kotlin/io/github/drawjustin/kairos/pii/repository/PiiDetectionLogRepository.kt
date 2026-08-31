package io.github.drawjustin.kairos.pii.repository

import io.github.drawjustin.kairos.pii.entity.PiiDetectionLog
import org.springframework.data.jpa.repository.JpaRepository

interface PiiDetectionLogRepository : JpaRepository<PiiDetectionLog, Long> {
    fun findAllByProject_IdOrderByIdAsc(projectId: Long): List<PiiDetectionLog>
}
