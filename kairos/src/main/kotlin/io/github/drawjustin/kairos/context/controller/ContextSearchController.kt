package io.github.drawjustin.kairos.context.controller

import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.context.dto.ContextSearchRequest
import io.github.drawjustin.kairos.context.dto.ContextSearchResponse
import io.github.drawjustin.kairos.context.dto.ContextSourcesResponse
import io.github.drawjustin.kairos.context.service.ContextSearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/context")
@Tag(name = "Context", description = "개발 AI 도구가 사내 참조자료를 안전하게 검색하는 Context Gateway API")
class ContextSearchController(
    private val contextSearchService: ContextSearchService,
) {
    @GetMapping("/sources")
    @Operation(
        summary = "사용 가능한 context source 목록 조회",
        description = "Claude Code, Codex, Antigravity 같은 개발 AI 도구가 검색 전에 project에서 사용할 수 있는 context source 목록과 설명을 조회한다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "context source 목록 조회 성공",
                headers = [Header(name = "X-Trace-Id", description = "요청 추적용 trace identifier")],
            ),
            ApiResponse(
                responseCode = "401",
                description = "access token이 없거나 유효하지 않음",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "project 접근 권한 없음",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "project를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
        ],
    )
    fun listSources(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestParam projectId: Long,
    ): ContextSourcesResponse = ContextSourcesResponse(
        result = contextSearchService.listSources(principal, projectId),
    )

    @PostMapping("/search")
    @Operation(
        summary = "사내 참조자료 검색",
        description = "Claude Code, Codex, Antigravity 같은 개발 AI 도구가 project scope 안에서 허용된 context source만 검색한다. LLM provider를 호출하지 않고 참조자료 목록만 반환한다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "context 검색 성공",
                headers = [Header(name = "X-Trace-Id", description = "요청 추적용 trace identifier")],
            ),
            ApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "access token이 없거나 유효하지 않음",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "project 접근 권한 없음",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "project를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
        ],
    )
    fun search(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @Parameter(description = "검색 요청")
        @Valid @RequestBody request: ContextSearchRequest,
    ): ContextSearchResponse = ContextSearchResponse(
        result = contextSearchService.search(principal, request),
    )
}
