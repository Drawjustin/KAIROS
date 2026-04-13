package io.github.drawjustin.kairos.platform.controller

import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.platform.dto.ApiKeyIssueResponse
import io.github.drawjustin.kairos.platform.dto.ApiKeysResponse
import io.github.drawjustin.kairos.platform.dto.CreateApiKeyRequest
import io.github.drawjustin.kairos.platform.dto.CreateProjectRequest
import io.github.drawjustin.kairos.platform.dto.ProjectResponse
import io.github.drawjustin.kairos.platform.dto.ProjectsResponse
import io.github.drawjustin.kairos.platform.service.PlatformManagementService
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/platform")
@Tag(name = "Platform", description = "tenant 내부의 project, API key를 관리하는 일반 플랫폼 API")
// 일반 사용자는 자신이 접근 가능한 tenant 내부 리소스만 다룬다.
class PlatformController(
    private val platformManagementService: PlatformManagementService,
) {
    @PostMapping("/tenants/{tenantId}/projects")
    @Operation(summary = "project 생성", description = "지정한 tenant 아래에 새 project를 생성한다. tenant owner 또는 ADMIN만 가능하다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "project 생성 성공",
                headers = [Header(name = "X-Trace-Id", description = "요청 추적용 trace identifier")],
            ),
            ApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "tenant 접근 권한 없음",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"TENANT_003","errorMessage":"Access denied to tenant"}""")],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "tenant를 찾을 수 없음",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"TENANT_002","errorMessage":"Tenant not found"}""")],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "같은 tenant 안에 이미 존재하는 project 이름",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"PROJECT_001","errorMessage":"Project already exists"}""")],
                    ),
                ],
            ),
        ],
    )
    // project는 항상 tenant 아래에 매달리도록 경로에서 부모를 명시한다.
    fun createProject(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @Parameter(description = "project를 생성할 상위 tenant ID", example = "1")
        @PathVariable tenantId: Long,
        @Valid @RequestBody request: CreateProjectRequest,
    ): ProjectResponse = ProjectResponse(
        result = platformManagementService.createProject(principal, tenantId, request),
    )

    @GetMapping("/tenants/{tenantId}/projects")
    @Operation(summary = "tenant의 project 목록 조회", description = "지정한 tenant 아래의 project 목록을 조회한다. tenant owner 또는 ADMIN만 가능하다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "project 목록 조회 성공",
                headers = [Header(name = "X-Trace-Id", description = "요청 추적용 trace identifier")],
            ),
            ApiResponse(
                responseCode = "403",
                description = "tenant 접근 권한 없음",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "tenant를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
        ],
    )
    // 목록 조회도 같은 tenant 경로를 써서 소유권 검사를 재사용한다.
    fun listProjects(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @Parameter(description = "project 목록을 조회할 tenant ID", example = "1")
        @PathVariable tenantId: Long,
    ): ProjectsResponse = ProjectsResponse(
        result = platformManagementService.listProjects(principal, tenantId),
    )

    @PostMapping("/projects/{projectId}/api-keys")
    @Operation(summary = "API key 발급", description = "지정한 project 아래에 새 API key를 발급한다. 원문 key는 생성 응답에서 한 번만 노출된다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "API key 발급 성공",
                headers = [Header(name = "X-Trace-Id", description = "요청 추적용 trace identifier")],
            ),
            ApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "project 접근 권한 없음",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"PROJECT_003","errorMessage":"Access denied to project"}""")],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "project를 찾을 수 없음",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"PROJECT_002","errorMessage":"Project not found"}""")],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "같은 project 안에 이미 존재하는 API key 이름",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"APIKEY_001","errorMessage":"API key already exists"}""")],
                    ),
                ],
            ),
        ],
    )
    // API key는 project 단위로 발급해 이후 사용량/비용을 더 세밀하게 묶을 수 있다.
    fun createApiKey(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @Parameter(description = "API key를 발급할 project ID", example = "1")
        @PathVariable projectId: Long,
        @Valid @RequestBody request: CreateApiKeyRequest,
    ): ApiKeyIssueResponse = ApiKeyIssueResponse(
        result = platformManagementService.createApiKey(principal, projectId, request),
    )

    @GetMapping("/projects/{projectId}/api-keys")
    @Operation(summary = "project의 API key 목록 조회", description = "지정한 project 아래의 API key 메타데이터 목록을 조회한다. 원문 key는 다시 반환하지 않는다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "API key 목록 조회 성공",
                headers = [Header(name = "X-Trace-Id", description = "요청 추적용 trace identifier")],
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
    // 원문 key는 다시 보여주지 않고 preview만 목록에 남긴다.
    fun listApiKeys(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @Parameter(description = "API key 목록을 조회할 project ID", example = "1")
        @PathVariable projectId: Long,
    ): ApiKeysResponse = ApiKeysResponse(
        result = platformManagementService.listApiKeys(principal, projectId),
    )
}
