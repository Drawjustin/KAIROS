package io.github.drawjustin.kairos.platform.controller

import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.platform.dto.ApiKeyIssueResponse
import io.github.drawjustin.kairos.platform.dto.ApiKeysResponse
import io.github.drawjustin.kairos.platform.dto.CreateApiKeyRequest
import io.github.drawjustin.kairos.platform.dto.CreateProjectRequest
import io.github.drawjustin.kairos.platform.dto.CreateTenantRequest
import io.github.drawjustin.kairos.platform.dto.ProjectResponse
import io.github.drawjustin.kairos.platform.dto.ProjectsResponse
import io.github.drawjustin.kairos.platform.dto.TenantResponse
import io.github.drawjustin.kairos.platform.dto.TenantsResponse
import io.github.drawjustin.kairos.platform.service.PlatformManagementService
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
// 플랫폼 운영의 최소 리소스인 tenant, project, API key를 관리한다.
class PlatformController(
    private val platformManagementService: PlatformManagementService,
) {
    @PostMapping("/tenants")
    // tenant 생성은 플랫폼 운영 리소스를 만드는 첫 진입점이다.
    fun createTenant(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @Valid @RequestBody request: CreateTenantRequest,
    ): TenantResponse = TenantResponse(
        result = platformManagementService.createTenant(principal, request),
    )



    @PostMapping("/tenants/{tenantId}/projects")
    // project는 항상 tenant 아래에 매달리도록 경로에서 부모를 명시한다.
    fun createProject(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable tenantId: Long,
        @Valid @RequestBody request: CreateProjectRequest,
    ): ProjectResponse = ProjectResponse(
        result = platformManagementService.createProject(principal, tenantId, request),
    )

    @GetMapping("/tenants/{tenantId}/projects")
    // 목록 조회도 같은 tenant 경로를 써서 소유권 검사를 재사용한다.
    fun listProjects(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable tenantId: Long,
    ): ProjectsResponse = ProjectsResponse(
        result = platformManagementService.listProjects(principal, tenantId),
    )

    @PostMapping("/projects/{projectId}/api-keys")
    // API key는 project 단위로 발급해 이후 사용량/비용을 더 세밀하게 묶을 수 있다.
    fun createApiKey(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable projectId: Long,
        @Valid @RequestBody request: CreateApiKeyRequest,
    ): ApiKeyIssueResponse = ApiKeyIssueResponse(
        result = platformManagementService.createApiKey(principal, projectId, request),
    )

    @GetMapping("/projects/{projectId}/api-keys")
    // 원문 key는 다시 보여주지 않고 preview만 목록에 남긴다.
    fun listApiKeys(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @PathVariable projectId: Long,
    ): ApiKeysResponse = ApiKeysResponse(
        result = platformManagementService.listApiKeys(principal, projectId),
    )
}
