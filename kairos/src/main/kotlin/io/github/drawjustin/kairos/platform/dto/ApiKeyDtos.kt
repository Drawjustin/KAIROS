package io.github.drawjustin.kairos.platform.dto

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateApiKeyRequest(
    @field:Schema(description = "API key 이름", example = "chatbot-prod-default")
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
    @field:Schema(description = "만료 시각. 없으면 만료 없음", example = "2026-12-31T23:59:59Z")
    val expiresAt: Instant? = null,
)

data class ApiKeyOutput(
    @field:Schema(description = "API key ID", example = "1")
    val id: Long,
    @field:Schema(description = "소속 project ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "API key 이름", example = "chatbot-prod-default")
    val name: String,
    @field:Schema(description = "목록에서 식별할 수 있는 key preview", example = "kairos_sk_ab12cd34...")
    val keyPreview: String,
    @field:Schema(description = "만료 시각", example = "2026-12-31T23:59:59Z")
    val expiresAt: Instant?,
    @field:Schema(description = "마지막 사용 시각", example = "2026-04-13T11:00:00Z")
    val lastUsedAt: Instant?,
    @field:Schema(description = "폐기 시각", example = "2026-04-13T12:00:00Z")
    val revokedAt: Instant?,
    @field:Schema(description = "생성 시각", example = "2026-04-13T10:10:00Z")
    val createdAt: Instant,
)

data class ApiKeyIssueOutput(
    @field:Schema(description = "생성 직후 한 번만 노출되는 API key 원문", example = "kairos_sk_xxxxxxxxxxxxxxxxxxxx")
    val apiKey: String,
    @field:Schema(description = "생성된 API key 메타데이터")
    val key: ApiKeyOutput,
)

data class ApiKeyIssueResponse(
    @field:Schema(description = "방금 발급된 API key와 메타데이터")
    val result: ApiKeyIssueOutput,
) : BaseOutput()

data class ApiKeysResponse(
    @field:Schema(description = "조회된 API key 메타데이터 목록")
    val result: List<ApiKeyOutput>,
) : BaseOutput()
