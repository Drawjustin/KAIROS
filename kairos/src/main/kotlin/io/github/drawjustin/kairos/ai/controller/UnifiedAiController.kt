package io.github.drawjustin.kairos.ai.controller

import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.service.UnifiedAiService
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Chat AI", description = "provider 차이를 숨기고 공통 chat completion API를 제공한다")
class UnifiedAiController(
    private val unifiedAiService: UnifiedAiService,
) {
    @PostMapping("/chat/completions")
    @Operation(summary = "chat completion 호출", description = "OpenAI 호환 형태의 공통 chat completion 엔드포인트다. 현재는 non-streaming 요청과 OpenAI, Claude provider를 지원한다.")
    @SecurityRequirement(name = "apiKeyBearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "chat completion 성공",
                headers = [Header(name = "X-Trace-Id", description = "요청 추적용 trace identifier")],
            ),
            ApiResponse(
                responseCode = "400",
                description = "입력값 오류 또는 아직 지원하지 않는 요청",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "유효하지 않거나 만료된 API key",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"AI_001","errorMessage":"Invalid API key"}""")],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "500",
                description = "provider 설정 오류 또는 provider 호출 실패",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
        ],
    )
    // Unified AI API는 JWT 대신 project API key를 Authorization 헤더로 받는다.
    fun chatCompletion(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorizationHeader: String?,
        @Valid @RequestBody request: ChatCompletionRequest,
    ): ChatCompletionResponse = unifiedAiService.chatCompletion(authorizationHeader, request)
}
