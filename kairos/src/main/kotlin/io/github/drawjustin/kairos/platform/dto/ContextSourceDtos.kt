package io.github.drawjustin.kairos.platform.dto

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.context.type.ContextSourceStatus
import io.github.drawjustin.kairos.context.type.ContextSourceType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateProjectContextSourceRequest(
    @field:Schema(description = "context source 이름", example = "hr-policy-manual")
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
    @field:Schema(description = "context source 유형", example = "DOCUMENT")
    val type: ContextSourceType,
    @field:Schema(description = "설명", example = "인사 정책 매뉴얼")
    @field:Size(max = 500)
    val description: String? = null,
    @field:Schema(description = "외부 문서/도구/MCP 서버 URI", example = "https://wiki.example.com/hr-policy")
    @field:Size(max = 1000)
    val uri: String? = null,
)

data class ContextSourceOutput(
    @field:Schema(description = "context source ID", example = "1")
    val id: Long,
    @field:Schema(description = "context source 유형", example = "DOCUMENT")
    val type: ContextSourceType,
    @field:Schema(description = "context source 이름", example = "hr-policy-manual")
    val name: String,
    @field:Schema(description = "설명", example = "인사 정책 매뉴얼")
    val description: String?,
    @field:Schema(description = "외부 문서/도구/MCP 서버 URI", example = "https://wiki.example.com/hr-policy")
    val uri: String?,
    @field:Schema(description = "상태", example = "ACTIVE")
    val status: ContextSourceStatus,
    @field:Schema(description = "생성 시각", example = "2026-04-26T10:05:00Z")
    val createdAt: Instant,
)

data class ContextSourceResponse(
    @field:Schema(description = "context source 정보")
    val result: ContextSourceOutput,
) : BaseOutput()

data class ContextSourcesResponse(
    @field:Schema(description = "조회된 context source 목록")
    val result: List<ContextSourceOutput>,
) : BaseOutput()
