package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.provider.ProviderAdapter
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
// 공통 요청 검증과 provider 호출을 한곳에 모아 초기 버전의 호출 흐름을 단순하게 유지한다.
class UnifiedAiService(
    private val aiApiKeyService: AiApiKeyService,
    @param:Qualifier("openAiProviderAdapter")
    private val providerAdapter: ProviderAdapter,
) {
    fun chatCompletion(
        authorizationHeader: String?,
        request: ChatCompletionRequest,
    ): ChatCompletionResponse {

        if (request.stream) {
            throw KairosException(KairosErrorCode.AI_STREAM_NOT_SUPPORTED)
        }

        val apiKey = extractBearerToken(authorizationHeader)
        aiApiKeyService.authenticate(apiKey)
        return providerAdapter.chatCompletion(request)
    }

    private fun extractBearerToken(authorizationHeader: String?): String {
        val header = authorizationHeader?.trim().orEmpty()

        if (!header.startsWith("Bearer ")) {
            throw KairosException(KairosErrorCode.AI_INVALID_API_KEY)
        }
        return header.removePrefix("Bearer ").trim()
    }
}
