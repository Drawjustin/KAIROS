package io.github.drawjustin.kairos.platform.dto

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.context.type.ContextSourceStatus
import io.github.drawjustin.kairos.context.type.ContextSourceType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateProjectContextSourceRequest(
    @field:Schema(description = "context source 이름", example = "hr_policy_search")
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
    @field:Schema(description = "context source 유형", example = "MCP_SERVER")
    val type: ContextSourceType,
    @field:Schema(
        description = "AI가 이 tool을 언제 선택해야 하는지 알려주는 설명",
        example = "연차, 휴가, 재택근무, 복지, 인사 규정, 휴직, 경조사, 근태에 대한 질문이 들어오면 사용한다. 사내 HR 정책 문서에서 관련 내용을 검색한다. 개인 급여, 인사평가, 징계 기록 같은 개인 민감정보 조회에는 사용하지 않는다.",
    )
    @field:Size(max = 500)
    val description: String? = null,
    @field:Schema(description = "내부 문서 검색 API 또는 MCP gateway URI", example = "http://localhost:8080/internal/tools/mock-doc-search")
    @field:Size(max = 1000)
    val uri: String? = null,
)

data class ContextSourceOutput(
    @field:Schema(description = "context source ID", example = "1")
    val id: Long,
    @field:Schema(description = "context source 유형", example = "MCP_SERVER")
    val type: ContextSourceType,
    @field:Schema(description = "context source 이름", example = "hr_policy_search")
    val name: String,
    @field:Schema(
        description = "AI가 이 tool을 언제 선택해야 하는지 알려주는 설명",
        example = "연차, 휴가, 재택근무, 복지, 인사 규정, 휴직, 경조사, 근태에 대한 질문이 들어오면 사용한다. 사내 HR 정책 문서에서 관련 내용을 검색한다. 개인 급여, 인사평가, 징계 기록 같은 개인 민감정보 조회에는 사용하지 않는다.",
    )
    val description: String?,
    @field:Schema(description = "내부 문서 검색 API 또는 MCP gateway URI", example = "http://localhost:8080/internal/tools/mock-doc-search")
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
