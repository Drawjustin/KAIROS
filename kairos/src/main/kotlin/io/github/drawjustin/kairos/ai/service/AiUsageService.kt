package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.ai.repository.AiUsageModelBreakdownRow
import io.github.drawjustin.kairos.ai.repository.AiUsageProjectBreakdownRow
import io.github.drawjustin.kairos.ai.repository.AiUsageQueryRepository
import io.github.drawjustin.kairos.ai.repository.AiUsageSummaryRow
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.platform.dto.AiUsageModelBreakdownOutput
import io.github.drawjustin.kairos.platform.dto.AiUsageProjectBreakdownOutput
import io.github.drawjustin.kairos.platform.dto.AiUsageSummaryQuery
import io.github.drawjustin.kairos.platform.dto.ProjectAiUsageSummaryOutput
import io.github.drawjustin.kairos.platform.dto.ProjectAiUsageSummaryQuery
import io.github.drawjustin.kairos.platform.dto.TenantAiUsageSummaryOutput
import io.github.drawjustin.kairos.platform.dto.TenantAiUsageSummaryQuery
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
        val (resolvedFrom, resolvedTo) = query.resolveRange()

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

    @Transactional(readOnly = true)
    fun getTenantUsageSummary(tenantId: Long, query: TenantAiUsageSummaryQuery): TenantAiUsageSummaryOutput {
        val (resolvedFrom, resolvedTo) = query.resolveRange()

        val summary = aiUsageQueryRepository.summarizeTenantUsage(
            tenantId = tenantId,
            from = resolvedFrom,
            to = resolvedTo,
        )
        val byProject = aiUsageQueryRepository.summarizeTenantUsageByProject(
            tenantId = tenantId,
            from = resolvedFrom,
            to = resolvedTo,
        )

        return summary.toTenantAiUsageSummaryOutput(
            tenantId = tenantId,
            from = resolvedFrom,
            to = resolvedTo,
            byProject = byProject.map { it.toOutput() },
        )
    }

    private fun AiUsageSummaryQuery.resolveRange(): Pair<Instant, Instant> {
        val resolvedTo = to ?: Instant.now()
        val resolvedFrom = from ?: resolvedTo.minus(DEFAULT_USAGE_LOOKBACK_DAYS, ChronoUnit.DAYS)
        if (!resolvedFrom.isBefore(resolvedTo)) {
            throw KairosException(KairosErrorCode.INVALID_INPUT, "from must be before to")
        }
        return resolvedFrom to resolvedTo
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

    private fun AiUsageSummaryRow.toTenantAiUsageSummaryOutput(
        tenantId: Long,
        from: Instant,
        to: Instant,
        byProject: List<AiUsageProjectBreakdownOutput>,
    ): TenantAiUsageSummaryOutput = TenantAiUsageSummaryOutput(
        tenantId = tenantId,
        from = from,
        to = to,
        totalRequests = totalRequests,
        successRequests = successRequests,
        failedRequests = failedRequests,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
        byProject = byProject,
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

    private fun AiUsageProjectBreakdownRow.toOutput(): AiUsageProjectBreakdownOutput =
        AiUsageProjectBreakdownOutput(
            projectId = projectId,
            projectName = projectName,
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
