package io.github.drawjustin.kairos.context.repository

import io.github.drawjustin.kairos.context.entity.ContextSource
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface ContextSourceRepository : JpaRepository<ContextSource, Long> {
    fun existsByNameIgnoreCaseAndDeletedAtIsNull(name: String): Boolean

    fun findByNameIgnoreCaseAndDeletedAtIsNull(name: String): Optional<ContextSource>

    fun findByIdAndDeletedAtIsNull(id: Long): Optional<ContextSource>
}
