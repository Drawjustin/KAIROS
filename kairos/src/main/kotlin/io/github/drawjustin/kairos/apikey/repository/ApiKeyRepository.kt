package io.github.drawjustin.kairos.apikey.repository

import io.github.drawjustin.kairos.apikey.entity.ApiKey
import org.springframework.data.jpa.repository.JpaRepository

interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    fun existsByProject_IdAndNameIgnoreCaseAndDeletedAtIsNull(projectId: Long, name: String): Boolean

    fun findAllByProject_IdAndDeletedAtIsNullOrderByCreatedAtAsc(projectId: Long): List<ApiKey>

    fun findByKeyHashAndDeletedAtIsNullAndRevokedAtIsNull(keyHash: String): ApiKey?
}
