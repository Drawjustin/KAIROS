package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.provider.ProviderRouter
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.apikey.entity.ApiKey
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
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
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
        val callResult = try {
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
            callWithFallback(
                projectId = projectId,
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
            recordFailureWithoutMaskingCause(credential, request.model, startedAt, exception.errorCode.code, exception)
            throw exception
        } catch (exception: CallNotPermittedException) {
            // 서킷이 열려 있어 호출조차 하지 않은 상태다.
            // 죽은 provider를 계속 두드리며 지연을 쌓는 대신 즉시 실패를 돌려준다.
            budgetGuard.release(reservation)
            val failure = KairosException(KairosErrorCode.AI_PROVIDER_UNAVAILABLE)
            recordFailureWithoutMaskingCause(
                credential, request.model, startedAt, KairosErrorCode.AI_PROVIDER_UNAVAILABLE.code, failure,
            )
            throw failure
        } catch (exception: BulkheadFullException) {
            // 동시 호출 제한에 걸린 것은 장애가 아니라 격리가 동작한 결과다.
            // 서버 오류로 묶어버리면 운영에서 진짜 장애와 구분할 수 없다.
            budgetGuard.release(reservation)
            val failure = KairosException(KairosErrorCode.AI_PROVIDER_OVERLOADED)
            recordFailureWithoutMaskingCause(
                credential, request.model, startedAt, KairosErrorCode.AI_PROVIDER_OVERLOADED.code, failure,
            )
            throw failure
        } catch (exception: Exception) {
            budgetGuard.release(reservation)
            recordFailureWithoutMaskingCause(
                credential, request.model, startedAt, KairosErrorCode.INTERNAL_SERVER_ERROR.code, exception,
            )
            throw exception
        }
        val latencyMs = elapsedMillis(startedAt)

        // 호출 전에는 토큰 수를 알 수 없으므로 응답이 온 지금 실제 사용량으로 정산한다.
        budgetGuard.settle(reservation, callResult.response.usage?.totalTokens ?: 0)
        // 사용량은 실제로 응답한 모델 기준으로 남긴다. 대체가 일어났다면 원래 요청한 모델도 함께 기록한다.
        //
        // 기록에 실패하면 응답을 내보내지 않는다(fail-closed). 의도한 선택이다.
        // 이미 발생한 호출 비용을 버리게 되지만, 누가 무엇을 외부로 보냈는지 남지 않은 채
        // 결과만 나가면 그 호출은 사후에 재구성할 수 없다. 추적할 수 없는 호출이 비용보다 큰 문제다.
        // 원장을 남기지 못했다는 사실 자체를 별도 코드로 구분해 일반 서버 오류와 섞이지 않게 한다.
        try {
            aiUsageLoggingService.recordSuccess(
                apiKey = credential,
                model = callResult.model,
                response = callResult.response,
                latencyMs = latencyMs,
                fallbackFromModel = callResult.fallbackFromModel,
            )
        } catch (exception: Exception) {
            throw KairosException(KairosErrorCode.AI_AUDIT_LOG_FAILED).apply { addSuppressed(exception) }
        }
        return callResult.response
    }

    // provider가 장애일 때 project가 이미 허용한 다른 provider의 모델로 넘긴다.
    // 후보를 project_allowed_model에서만 고르므로 장애 대응이 권한 통제를 우회하는 일은 없다.
    // 가용성보다 통제를 우선한 선택이고, 그래서 허용된 대체 모델이 없으면 그냥 실패한다.
    private fun callWithFallback(
        projectId: Long,
        request: ChatCompletionRequest,
        tools: List<AiToolDefinition>,
        toolExecutionContext: AiToolExecutionContext,
    ): ProviderCallResult {
        val primaryFailure = try {
            return ProviderCallResult(
                response = invokeProvider(request, tools, toolExecutionContext),
                model = request.model,
            )
        } catch (exception: Exception) {
            if (!exception.isProviderUnavailable()) {
                throw exception
            }
            exception
        }

        fallbackCandidates(projectId, request.model).forEach { candidate ->
            try {
                return ProviderCallResult(
                    response = invokeProvider(request.copy(model = candidate), tools, toolExecutionContext),
                    model = candidate,
                    fallbackFromModel = request.model,
                )
            } catch (exception: Exception) {
                // 후보 하나가 실패했다고 나머지를 포기하지 않는다.
                // 여기서 예외를 그대로 올리면 아직 멀쩡한 provider가 남아 있는데도 요청이 끝나고,
                // 사용자는 원래 장애 대신 마지막 후보가 낸 엉뚱한 오류를 받게 된다.
                primaryFailure.addSuppressed(exception)
            }
        }
        // 대체할 곳이 없거나 후보가 모두 실패했다면 원래 장애를 그대로 돌려준다.
        throw primaryFailure
    }

    // 같은 provider의 다른 모델로 넘겨봐야 같은 장애를 다시 만난다. provider가 다른 모델만 후보다.
    private fun fallbackCandidates(projectId: Long, failedModel: AiModel): List<AiModel> =
        projectAllowedModelRepository.findAllByProject_IdAndDeletedAtIsNullOrderByModelAsc(projectId)
            .map { it.model }
            .filter { it.provider != failedModel.provider }
            .distinct()

    private fun invokeProvider(
        request: ChatCompletionRequest,
        tools: List<AiToolDefinition>,
        toolExecutionContext: AiToolExecutionContext,
    ): ChatCompletionResponse =
        providerRouter.route(request.model).chatCompletion(
            request = request,
            tools = tools,
            toolExecutionContext = toolExecutionContext,
        )

    // 서킷이 열렸거나 provider가 일시적으로 응답하지 못하는 상황만 대체 대상이다.
    // 잘못된 요청을 다른 provider에 넘기면 거기서도 똑같이 거절당한다.
    private fun Exception.isProviderUnavailable(): Boolean =
        this is CallNotPermittedException ||
            (this is KairosException && errorCode == KairosErrorCode.AI_PROVIDER_UNAVAILABLE)

    private data class ProviderCallResult(
        val response: ChatCompletionResponse,
        val model: AiModel,
        val fallbackFromModel: AiModel? = null,
    )

    // 실패 경로에서는 성공 경로와 반대로 기록 실패를 삼킨다.
    // 요청은 어차피 실패로 끝나므로 막아야 할 "기록 없는 성공"이 존재하지 않고,
    // 여기서 예외를 올리면 원래 실패 원인이 DB 오류로 바뀌어 진짜 문제를 못 보게 된다.
    // 기록 실패 사실은 suppressed로 붙여 스택트레이스에 함께 남긴다.
    private fun recordFailureWithoutMaskingCause(
        credential: ApiKey,
        model: AiModel,
        startedAt: Long,
        errorCode: String,
        cause: Throwable,
    ) {
        try {
            aiUsageLoggingService.recordFailure(
                apiKey = credential,
                model = model,
                latencyMs = elapsedMillis(startedAt),
                errorCode = errorCode,
            )
        } catch (exception: Exception) {
            cause.addSuppressed(exception)
        }
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
