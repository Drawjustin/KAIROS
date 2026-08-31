package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.provider.ProviderRouter
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.github.drawjustin.kairos.budget.service.BudgetGuard
import io.github.drawjustin.kairos.budget.service.BudgetReservation
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.context.type.ContextSearchPurpose
import io.github.drawjustin.kairos.pii.service.PiiGuard
import io.github.drawjustin.kairos.pii.type.PiiInspectionSource
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.project.repository.ProjectAllowedModelRepository
import io.github.resilience4j.bulkhead.BulkheadFullException
import org.springframework.stereotype.Service

@Service
// 공통 요청 검증과 provider 호출을 한곳에 모아 초기 버전의 호출 흐름을 단순하게 유지한다.
class UnifiedAiService(
    private val aiApiKeyService: AiApiKeyService,
    private val providerRouter: ProviderRouter,
    private val aiUsageLoggingService: AiUsageLoggingService,
    private val projectAllowedModelRepository: ProjectAllowedModelRepository,
    private val projectContextToolService: ProjectContextToolService,
    private val piiGuard: PiiGuard,
    private val budgetGuard: BudgetGuard,
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
        val startedAt = System.nanoTime()
        // 선점에 실패하면 되돌릴 것이 없으므로 빈 예약으로 시작한다.
        var reservation = BudgetReservation.none(projectId)
        val response = try {
            validateAllowedModel(
                projectId = projectId,
                request = request,
            )
            // 민감정보 검사는 provider 호출 직전에 수행한다.
            // 차단되면 그대로 예외가 되어 아래 catch에서 실패 사용량으로 기록된다.
            val guardedRequest = request
                .withInspectedMessages(credential.project)
                .withDefaultSystemPrompt()
            // 예산 선점은 민감정보 검사를 통과한 뒤에 한다.
            // 차단될 요청이 먼저 한도를 갉아먹으면 정작 정상 요청이 밀려난다.
            reservation = budgetGuard.reserve(projectId)
            val providerAdapter = providerRouter.route(guardedRequest.model)
            providerAdapter.chatCompletion(
                request = guardedRequest,
                tools = tools,
                toolExecutionContext = AiToolExecutionContext(
                    userId = requireNotNull(credential.createdByUser.id) { "API key creator id must exist" },
                    project = credential.project,
                    purpose = ContextSearchPurpose.INTERNAL_QA,
                ),
            )
        } catch (exception: KairosException) {
            budgetGuard.release(reservation)
            aiUsageLoggingService.recordFailure(
                apiKey = credential,
                model = request.model,
                latencyMs = elapsedMillis(startedAt),
                errorCode = exception.errorCode.code,
            )
            throw exception
        } catch (exception: BulkheadFullException) {
            // 동시 호출 제한에 걸린 것은 장애가 아니라 격리가 동작한 결과다.
            // 서버 오류로 묶어버리면 운영에서 진짜 장애와 구분할 수 없다.
            budgetGuard.release(reservation)
            aiUsageLoggingService.recordFailure(
                apiKey = credential,
                model = request.model,
                latencyMs = elapsedMillis(startedAt),
                errorCode = KairosErrorCode.AI_PROVIDER_OVERLOADED.code,
            )
            throw KairosException(KairosErrorCode.AI_PROVIDER_OVERLOADED)
        } catch (exception: Exception) {
            budgetGuard.release(reservation)
            aiUsageLoggingService.recordFailure(
                apiKey = credential,
                model = request.model,
                latencyMs = elapsedMillis(startedAt),
                errorCode = KairosErrorCode.INTERNAL_SERVER_ERROR.code,
            )
            throw exception
        }
        val latencyMs = elapsedMillis(startedAt)

        // 호출 전에는 토큰 수를 알 수 없으므로 응답이 온 지금 실제 사용량으로 정산한다.
        budgetGuard.settle(reservation, response.usage?.totalTokens ?: 0)
        aiUsageLoggingService.recordSuccess(
            apiKey = credential,
            model = request.model,
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

    // KAIROS가 붙이는 시스템 프롬프트에는 민감정보가 없으므로 사용자 메시지만 검사한다.
    // 메시지를 하나씩 검사하면 같은 요청이 감사 로그에 여러 건으로 흩어지므로 한 번에 넘긴다.
    private fun ChatCompletionRequest.withInspectedMessages(project: Project): ChatCompletionRequest {
        val inspectedContents = piiGuard.inspectAll(
            project = project,
            source = PiiInspectionSource.CHAT_PROMPT,
            texts = messages.map { it.content },
        )
        return copy(
            messages = messages.mapIndexed { index, message ->
                message.copy(content = inspectedContents[index])
            },
        )
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
