package io.github.drawjustin.kairos.ai.repository

import io.github.drawjustin.kairos.ai.entity.AiUsageLog
import org.springframework.data.jpa.repository.JpaRepository

interface AiUsageLogRepository : JpaRepository<AiUsageLog, Long>
