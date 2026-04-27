package io.github.drawjustin.kairos.context.dto

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.context.type.ContextSearchPurpose
import io.github.drawjustin.kairos.context.type.ContextSourceType
import io.github.drawjustin.kairos.platform.dto.ProjectOutput
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ContextSearchRequest(
    @field:Schema(description = "참조자료를 검색할 project ID", example = "1")
    @field:NotNull
    val projectId: Long,
    @field:Schema(description = "검색 질문 또는 검색어", example = "API 응답 포맷과 controller 작성 규칙")
    @field:NotBlank
    @field:Size(max = 2_000)
    val query: String,
    @field:Schema(description = "검색 목적", example = "CODE_ASSISTANT")
    val purpose: ContextSearchPurpose = ContextSearchPurpose.CODE_ASSISTANT,
    @field:Schema(
        description = "검색할 context source ID 목록. 비우면 project에 연결된 모든 source를 검색한다.",
        example = "[3]",
    )
    @field:Size(max = 20)
    val contextSourceIds: List<Long>? = null,
)

data class ContextSearchResponse(
    @field:Schema(description = "권한과 project scope를 통과한 참조자료 목록")
    val result: List<ContextSearchResult>,
) : BaseOutput()

data class ContextSourcesResponse(
    @field:Schema(description = "project에서 사용할 수 있는 context source 목록")
    val result: List<ContextSourceCatalogItem>,
) : BaseOutput()

data class ContextProjectsResponse(
    @field:Schema(description = "현재 사용자가 접근할 수 있는 project 목록")
    val result: List<ProjectOutput>,
) : BaseOutput()

data class ContextSourceCatalogItem(
    @field:Schema(description = "context source ID. 검색 요청의 contextSourceIds에 사용할 수 있다.", example = "3")
    val id: Long,
    @field:Schema(description = "context source 이름", example = "dev_docs_search")
    val name: String,
    @field:Schema(description = "context source 유형", example = "MCP_SERVER")
    val type: ContextSourceType,
    @field:Schema(description = "AI 도구가 이 source를 언제 사용해야 하는지 설명", example = "API 개발 규칙, 코딩 컨벤션, DB migration 규칙을 검색한다.")
    val description: String,
)

data class ContextSearchResult(
    @field:Schema(description = "참조자료 제목", example = "API Response Convention")
    val title: String,
    @field:Schema(description = "참조자료 내용 또는 문서 chunk", example = "모든 API 응답은 BaseOutput을 상속하고 result 필드에 데이터를 담는다.")
    val content: String,
    @field:Schema(description = "원본 문서 또는 내부 source 이름", example = "engineering-handbook")
    val source: String,
    @field:Schema(description = "검색 점수", example = "3")
    val score: Int,
    @field:Schema(description = "검색을 실행한 context source ID", example = "1")
    val contextSourceId: Long,
    @field:Schema(description = "검색을 실행한 context source 이름", example = "dev_docs_search")
    val contextSourceName: String,
    @field:Schema(description = "검색을 실행한 context source 유형", example = "MCP_SERVER")
    val contextSourceType: ContextSourceType,
)
