package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.ai.repository.AiUsageModelBreakdownRow
import io.github.drawjustin.kairos.ai.repository.AiUsageQueryRepository
import io.github.drawjustin.kairos.ai.repository.AiUsageSummaryRow
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.platform.dto.AiUsageModelBreakdownOutput
import io.github.drawjustin.kairos.platform.dto.ProjectAiUsageSummaryOutput
import io.github.drawjustin.kairos.platform.dto.ProjectAiUsageSummaryQuery
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
// AI 사용량 조회와 집계 결과 변환을 담당해 platform 관리 흐름에서 QueryDSL row 의존성을 분리한다.
class AiUsageService(
    private val aiUsageQueryRepository: AiUsageQueryRepository,
) {
    @Transactional(readOnly = true)
    fun getProjectUsageSummary(projectId: Long, query: ProjectAiUsageSummaryQuery): ProjectAiUsageSummaryOutput {
        val resolvedTo = query.to ?: Instant.now()
        val resolvedFrom = query.from ?: resolvedTo.minus(DEFAULT_USAGE_LOOKBACK_DAYS, ChronoUnit.DAYS)
        if (!resolvedFrom.isBefore(resolvedTo)) {
            throw KairosException(KairosErrorCode.INVALID_INPUT, "from must be before to")
        }

        val summary = aiUsageQueryRepository.summarizeProjectUsage(
            projectId = projectId,
            from = resolvedFrom,
            to = resolvedTo,
        )
        val byModel = aiUsageQueryRepository.summarizeProjectUsageByModel(
            projectId = projectId,
            from = resolvedFrom,
            to = resolvedTo,
        )

        return summary.toProjectAiUsageSummaryOutput(
            projectId = projectId,
            from = resolvedFrom,
            to = resolvedTo,
            byModel = byModel.map { it.toOutput() },
        )
    }

    private fun AiUsageSummaryRow.toProjectAiUsageSummaryOutput(
        projectId: Long,
        from: Instant,
        to: Instant,
        byModel: List<AiUsageModelBreakdownOutput>,
    ): ProjectAiUsageSummaryOutput = ProjectAiUsageSummaryOutput(
        projectId = projectId,
        from = from,
        to = to,
        totalRequests = totalRequests,
        successRequests = successRequests,
        failedRequests = failedRequests,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
        byModel = byModel,
    )

    private fun AiUsageModelBreakdownRow.toOutput(): AiUsageModelBreakdownOutput =
        AiUsageModelBreakdownOutput(
            provider = provider,
            model = model,
            totalRequests = totalRequests,
            successRequests = successRequests,
            failedRequests = failedRequests,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
        )

    private companion object {
        private const val DEFAULT_USAGE_LOOKBACK_DAYS = 30L
    }
}
