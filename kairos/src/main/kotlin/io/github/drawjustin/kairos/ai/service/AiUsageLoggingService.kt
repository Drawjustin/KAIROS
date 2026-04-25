package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.ai.dto.AiModel
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.entity.AiUsageLog
import io.github.drawjustin.kairos.ai.entity.AiUsageStatus
import io.github.drawjustin.kairos.ai.repository.AiUsageLogRepository
import io.github.drawjustin.kairos.apikey.entity.ApiKey
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
// AI 사용량 기록은 요청 성공/실패와 별개로 남아야 하므로 별도 트랜잭션으로 저장한다.
class AiUsageLoggingService(
    private val aiUsageLogRepository: AiUsageLogRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordSuccess(
        apiKey: ApiKey,
        model: AiModel,
        response: ChatCompletionResponse,
        latencyMs: Long,
    ) {
        val usage = response.usage
        aiUsageLogRepository.save(
            AiUsageLog(
                project = apiKey.project,
                apiKey = apiKey,
                provider = model.provider.name,
                model = model.value,
                inputTokens = usage?.promptTokens ?: 0,
                outputTokens = usage?.completionTokens ?: 0,
                totalTokens = usage?.totalTokens ?: 0,
                status = AiUsageStatus.SUCCESS,
                latencyMs = latencyMs,
                providerResponseId = response.id,
                traceId = currentTraceId(),
            ),
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(
        apiKey: ApiKey,
        model: AiModel,
        latencyMs: Long,
        errorCode: String,
    ) {
        aiUsageLogRepository.save(
            AiUsageLog(
                project = apiKey.project,
                apiKey = apiKey,
                provider = model.provider.name,
                model = model.value,
                status = AiUsageStatus.FAILED,
                latencyMs = latencyMs,
                errorCode = errorCode,
                traceId = currentTraceId(),
            ),
        )
    }

    private fun currentTraceId(): String? = MDC.get("traceId")
}
