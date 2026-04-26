package io.github.drawjustin.kairos.platform.dto

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import org.springframework.format.annotation.DateTimeFormat

data class AiUsageSummaryQuery(
    @field:Schema(description = "조회 시작 시각. 포함 조건", example = "2026-04-01T00:00:00Z")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val from: Instant? = null,
    @field:Schema(description = "조회 종료 시각. 미포함 조건", example = "2026-05-01T00:00:00Z")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val to: Instant? = null,
)

typealias ProjectAiUsageSummaryQuery = AiUsageSummaryQuery
typealias TenantAiUsageSummaryQuery = AiUsageSummaryQuery

data class AiUsageModelBreakdownOutput(
    @field:Schema(description = "AI provider 이름", example = "OPENAI")
    val provider: String,
    @field:Schema(description = "호출한 모델 ID", example = "gpt-4o-mini")
    val model: String,
    @field:Schema(description = "전체 요청 수", example = "120")
    val totalRequests: Long,
    @field:Schema(description = "성공 요청 수", example = "115")
    val successRequests: Long,
    @field:Schema(description = "실패 요청 수", example = "5")
    val failedRequests: Long,
    @field:Schema(description = "입력 토큰 수", example = "10000")
    val inputTokens: Long,
    @field:Schema(description = "출력 토큰 수", example = "4000")
    val outputTokens: Long,
    @field:Schema(description = "총 토큰 수", example = "14000")
    val totalTokens: Long,
)

data class ProjectAiUsageSummaryOutput(
    @field:Schema(description = "project ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "조회 시작 시각. 포함 조건", example = "2026-04-01T00:00:00Z")
    val from: Instant,
    @field:Schema(description = "조회 종료 시각. 미포함 조건", example = "2026-05-01T00:00:00Z")
    val to: Instant,
    @field:Schema(description = "전체 요청 수", example = "120")
    val totalRequests: Long,
    @field:Schema(description = "성공 요청 수", example = "115")
    val successRequests: Long,
    @field:Schema(description = "실패 요청 수", example = "5")
    val failedRequests: Long,
    @field:Schema(description = "입력 토큰 수", example = "10000")
    val inputTokens: Long,
    @field:Schema(description = "출력 토큰 수", example = "4000")
    val outputTokens: Long,
    @field:Schema(description = "총 토큰 수", example = "14000")
    val totalTokens: Long,
    @field:Schema(description = "provider/model별 사용량")
    val byModel: List<AiUsageModelBreakdownOutput>,
)

data class ProjectAiUsageSummaryResponse(
    @field:Schema(description = "project AI 사용량 요약")
    val result: ProjectAiUsageSummaryOutput,
) : BaseOutput()

data class AiUsageProjectBreakdownOutput(
    @field:Schema(description = "project ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "project 이름", example = "chatbot-prod")
    val projectName: String,
    @field:Schema(description = "전체 요청 수", example = "120")
    val totalRequests: Long,
    @field:Schema(description = "성공 요청 수", example = "115")
    val successRequests: Long,
    @field:Schema(description = "실패 요청 수", example = "5")
    val failedRequests: Long,
    @field:Schema(description = "입력 토큰 수", example = "10000")
    val inputTokens: Long,
    @field:Schema(description = "출력 토큰 수", example = "4000")
    val outputTokens: Long,
    @field:Schema(description = "총 토큰 수", example = "14000")
    val totalTokens: Long,
)

data class TenantAiUsageSummaryOutput(
    @field:Schema(description = "tenant ID", example = "1")
    val tenantId: Long,
    @field:Schema(description = "조회 시작 시각. 포함 조건", example = "2026-04-01T00:00:00Z")
    val from: Instant,
    @field:Schema(description = "조회 종료 시각. 미포함 조건", example = "2026-05-01T00:00:00Z")
    val to: Instant,
    @field:Schema(description = "전체 요청 수", example = "120")
    val totalRequests: Long,
    @field:Schema(description = "성공 요청 수", example = "115")
    val successRequests: Long,
    @field:Schema(description = "실패 요청 수", example = "5")
    val failedRequests: Long,
    @field:Schema(description = "입력 토큰 수", example = "10000")
    val inputTokens: Long,
    @field:Schema(description = "출력 토큰 수", example = "4000")
    val outputTokens: Long,
    @field:Schema(description = "총 토큰 수", example = "14000")
    val totalTokens: Long,
    @field:Schema(description = "project별 사용량")
    val byProject: List<AiUsageProjectBreakdownOutput>,
)

data class TenantAiUsageSummaryResponse(
    @field:Schema(description = "tenant AI 사용량 요약")
    val result: TenantAiUsageSummaryOutput,
) : BaseOutput()
