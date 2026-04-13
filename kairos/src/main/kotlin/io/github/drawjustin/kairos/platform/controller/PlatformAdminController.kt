package io.github.drawjustin.kairos.platform.controller

import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.platform.dto.CreateTenantRequest
import io.github.drawjustin.kairos.platform.dto.TenantResponse
import io.github.drawjustin.kairos.platform.dto.TenantsResponse
import io.github.drawjustin.kairos.platform.service.PlatformManagementService
import io.swagger.v3.oas.annotations.Operation
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/platform")
@Tag(name = "Platform Admin", description = "tenant를 관리하는 운영자 전용 플랫폼 API")
// 운영자 전용 기능은 일반 플랫폼 API와 경로를 분리해 권한 성격을 명확하게 드러낸다.
class PlatformAdminController(
    private val platformManagementService: PlatformManagementService,
) {
    @PostMapping("/tenants")
    @Operation(summary = "tenant 생성", description = "새 tenant를 생성한다. 현재는 ADMIN만 tenant를 만들 수 있다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "tenant 생성 성공",
                headers = [Header(name = "X-Trace-Id", description = "요청 추적용 trace identifier")],
            ),
            ApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "ADMIN 권한 없음",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"TENANT_000","errorMessage":"Only admins can create tenants"}""")],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 존재하는 tenant 이름",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"TENANT_001","errorMessage":"Tenant already exists"}""")],
                    ),
                ],
            ),
        ],
    )
    // tenant 생성은 운영자가 고객사/조직 단위를 등록하는 진입점이다.
    fun createTenant(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @Valid @RequestBody request: CreateTenantRequest,
    ): TenantResponse = TenantResponse(
        result = platformManagementService.createTenant(principal, request),
    )

    @GetMapping("/tenants")
    @Operation(summary = "전체 tenant 목록 조회", description = "플랫폼에 등록된 전체 tenant 목록을 조회한다. 현재는 ADMIN만 가능하다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "tenant 목록 조회 성공",
                headers = [Header(name = "X-Trace-Id", description = "요청 추적용 trace identifier")],
            ),
            ApiResponse(
                responseCode = "401",
                description = "access token이 없거나 유효하지 않음",
                content = [Content(schema = Schema(implementation = BaseOutput::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "ADMIN 권한 없음",
                content = [
                    Content(
                        schema = Schema(implementation = BaseOutput::class),
                        examples = [ExampleObject(value = """{"errorCode":"TENANT_003","errorMessage":"Access denied to tenant"}""")],
                    ),
                ],
            ),
        ],
    )
    // tenant 전체 목록은 운영자만 보게 해 관리 API 성격을 분명히 한다.
    fun listTenants(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): TenantsResponse = TenantsResponse(
        result = platformManagementService.listTenants(principal),
    )
}
