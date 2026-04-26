package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.provider.ProviderRouter
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.project.repository.ProjectAllowedModelRepository
import org.springframework.stereotype.Service

@Service
// 공통 요청 검증과 provider 호출을 한곳에 모아 초기 버전의 호출 흐름을 단순하게 유지한다.
class UnifiedAiService(
    private val aiApiKeyService: AiApiKeyService,
    private val providerRouter: ProviderRouter,
    private val aiUsageLoggingService: AiUsageLoggingService,
    private val projectAllowedModelRepository: ProjectAllowedModelRepository,
    private val projectContextToolService: ProjectContextToolService,
) {
    fun chatCompletion(
        authorizationHeader: String?,
        request: ChatCompletionRequest,
    ): ChatCompletionResponse {
        if (request.stream) {
            throw KairosException(KairosErrorCode.AI_STREAM_NOT_SUPPORTED)
        }

        val apiKey = extractBearerToken(authorizationHeader)
        val credential = aiApiKeyService.authenticate(apiKey)
        val projectId = requireNotNull(credential.project.id) { "API key project id must exist" }

        val tools = projectContextToolService.getProjectTools(projectId)
        val enrichedRequest = request.withDefaultSystemPrompt()
        val startedAt = System.nanoTime()
        val response = try {
            validateAllowedModel(
                projectId = projectId,
                request = enrichedRequest,
            )
            val providerAdapter = providerRouter.route(enrichedRequest.model)
            providerAdapter.chatCompletion(enrichedRequest, tools)
        } catch (exception: KairosException) {
            aiUsageLoggingService.recordFailure(
                apiKey = credential,
                model = enrichedRequest.model,
                latencyMs = elapsedMillis(startedAt),
                errorCode = exception.errorCode.code,
            )
            throw exception
        } catch (exception: Exception) {
            aiUsageLoggingService.recordFailure(
                apiKey = credential,
                model = enrichedRequest.model,
                latencyMs = elapsedMillis(startedAt),
                errorCode = KairosErrorCode.INTERNAL_SERVER_ERROR.code,
            )
            throw exception
        }
        val latencyMs = elapsedMillis(startedAt)

        aiUsageLoggingService.recordSuccess(
            apiKey = credential,
            model = enrichedRequest.model,
            response = response,
            latencyMs = latencyMs,
        )
        return response
    }

    private fun extractBearerToken(authorizationHeader: String?): String {
        val header = authorizationHeader?.trim().orEmpty()

        if (!header.startsWith("Bearer ")) {
            throw KairosException(KairosErrorCode.AI_INVALID_API_KEY)
        }
        return header.removePrefix("Bearer ").trim()
    }

    private fun validateAllowedModel(projectId: Long, request: ChatCompletionRequest) {
        if (!projectAllowedModelRepository.existsByProject_IdAndModelAndDeletedAtIsNull(projectId, request.model)) {
            throw KairosException(KairosErrorCode.AI_MODEL_NOT_ALLOWED)
        }
    }

    private fun ChatCompletionRequest.withDefaultSystemPrompt(): ChatCompletionRequest =
        copy(
            messages = listOf(
                ChatMessageRequest(
                    role = ChatRole.SYSTEM,
                    content = "너는 KAIROS의 사내 AI 어시스턴트다. 내부 문서를 우선 참고하고, 근거 없는 내용은 추측하지 마라.",
                ),
            ) + messages,
        )

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}
