package io.github.drawjustin.kairos.ai.repository

import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.drawjustin.kairos.ai.entity.AiUsageStatus
import io.github.drawjustin.kairos.ai.entity.QAiUsageLog
import java.time.Instant
import org.springframework.stereotype.Repository

@Repository
// 사용량 대시보드/리포트성 집계 조회는 QueryDSL로 타입 안전하게 분리한다.
class AiUsageQueryRepository(
    private val queryFactory: JPAQueryFactory,
) {
    private val aiUsageLog = QAiUsageLog.aiUsageLog

    fun summarizeProjectUsage(projectId: Long, from: Instant, to: Instant): AiUsageSummaryRow {
        val totalRequests = aiUsageLog.id.count()
        val successRequests = statusCount(AiUsageStatus.SUCCESS)
        val failedRequests = statusCount(AiUsageStatus.FAILED)
        val inputTokens = aiUsageLog.inputTokens.sum()
        val outputTokens = aiUsageLog.outputTokens.sum()
        val totalTokens = aiUsageLog.totalTokens.sum()

        val result = queryFactory
            .select(totalRequests, successRequests, failedRequests, inputTokens, outputTokens, totalTokens)
            .from(aiUsageLog)
            .where(
                aiUsageLog.project.id.eq(projectId),
                aiUsageLog.createdAt.goe(from),
                aiUsageLog.createdAt.lt(to),
            )
            .fetchOne()

        return AiUsageSummaryRow(
            totalRequests = result?.get(totalRequests) ?: 0,
            successRequests = result?.get(successRequests) ?: 0,
            failedRequests = result?.get(failedRequests) ?: 0,
            inputTokens = result?.get(inputTokens)?.toLong() ?: 0,
            outputTokens = result?.get(outputTokens)?.toLong() ?: 0,
            totalTokens = result?.get(totalTokens)?.toLong() ?: 0,
        )
    }

    fun summarizeProjectUsageByModel(projectId: Long, from: Instant, to: Instant): List<AiUsageModelBreakdownRow> {
        val totalRequests = aiUsageLog.id.count()
        val successRequests = statusCount(AiUsageStatus.SUCCESS)
        val failedRequests = statusCount(AiUsageStatus.FAILED)
        val inputTokens = aiUsageLog.inputTokens.sum()
        val outputTokens = aiUsageLog.outputTokens.sum()
        val totalTokens = aiUsageLog.totalTokens.sum()

        return queryFactory
            .select(
                aiUsageLog.provider,
                aiUsageLog.model,
                totalRequests,
                successRequests,
                failedRequests,
                inputTokens,
                outputTokens,
                totalTokens,
            )
            .from(aiUsageLog)
            .where(
                aiUsageLog.project.id.eq(projectId),
                aiUsageLog.createdAt.goe(from),
                aiUsageLog.createdAt.lt(to),
            )
            .groupBy(aiUsageLog.provider, aiUsageLog.model)
            .orderBy(
                totalTokens.desc(),
                totalRequests.desc(),
                aiUsageLog.provider.asc(),
                aiUsageLog.model.asc(),
            )
            .fetch()
            .map {
                AiUsageModelBreakdownRow(
                    provider = requireNotNull(it.get(aiUsageLog.provider)),
                    model = requireNotNull(it.get(aiUsageLog.model)),
                    totalRequests = it.get(totalRequests) ?: 0,
                    successRequests = it.get(successRequests) ?: 0,
                    failedRequests = it.get(failedRequests) ?: 0,
                    inputTokens = it.get(inputTokens)?.toLong() ?: 0,
                    outputTokens = it.get(outputTokens)?.toLong() ?: 0,
                    totalTokens = it.get(totalTokens)?.toLong() ?: 0,
                )
            }
    }

    private fun statusCount(status: AiUsageStatus) =
        CaseBuilder()
            .`when`(aiUsageLog.status.eq(status))
            .then(1L)
            .otherwise(0L)
            .sum()
}

data class AiUsageSummaryRow(
    val totalRequests: Long,
    val successRequests: Long,
    val failedRequests: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
)

data class AiUsageModelBreakdownRow(
    val provider: String,
    val model: String,
    val totalRequests: Long,
    val successRequests: Long,
    val failedRequests: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
)
