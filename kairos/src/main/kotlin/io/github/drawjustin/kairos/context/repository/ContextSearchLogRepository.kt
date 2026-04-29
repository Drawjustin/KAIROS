package io.github.drawjustin.kairos.context.repository

import io.github.drawjustin.kairos.context.entity.ContextSearchLog
import org.springframework.data.jpa.repository.JpaRepository

interface ContextSearchLogRepository : JpaRepository<ContextSearchLog, Long>
