package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.apikey.entity.ApiKey
import io.github.drawjustin.kairos.apikey.repository.ApiKeyRepository
import io.github.drawjustin.kairos.auth.service.TokenHasher
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.project.type.ProjectStatus
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory

@Service
// Unified AI API는 JWT 대신 발급된 project API key로 인증한다.
class AiApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val tokenHasher: TokenHasher,
) {
    @Transactional
    fun authenticate(rawApiKey: String): ApiKey {
        val apiKey = rawApiKey.trim()
        if (apiKey.isBlank() || !apiKey.startsWith("kairos_sk_")) {
            throw KairosException(KairosErrorCode.AI_INVALID_API_KEY)
        }
        val credential = apiKeyRepository.findByKeyHashAndDeletedAtIsNullAndRevokedAtIsNull(tokenHasher.hash(apiKey))
            ?: throw KairosException(KairosErrorCode.AI_INVALID_API_KEY)

        if (credential.expiresAt?.isBefore(Instant.now()) == true) {
            throw KairosException(KairosErrorCode.AI_API_KEY_EXPIRED)
        }
        if (credential.project.status != ProjectStatus.ACTIVE) {
            throw KairosException(KairosErrorCode.AI_PROJECT_INACTIVE)
        }

        credential.lastUsedAt = Instant.now()
        return credential
    }
}
