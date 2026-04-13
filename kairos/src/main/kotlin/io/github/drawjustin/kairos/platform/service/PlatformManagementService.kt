package io.github.drawjustin.kairos.platform.service

import io.github.drawjustin.kairos.apikey.entity.ApiKey
import io.github.drawjustin.kairos.apikey.repository.ApiKeyRepository
import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.auth.service.TokenHasher
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.platform.dto.ApiKeyIssueOutput
import io.github.drawjustin.kairos.platform.dto.ApiKeyOutput
import io.github.drawjustin.kairos.platform.dto.CreateApiKeyRequest
import io.github.drawjustin.kairos.platform.dto.CreateProjectRequest
import io.github.drawjustin.kairos.platform.dto.CreateTenantRequest
import io.github.drawjustin.kairos.platform.dto.ProjectOutput
import io.github.drawjustin.kairos.platform.dto.TenantOutput
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.entity.Tenant
import io.github.drawjustin.kairos.tenant.repository.TenantRepository
import io.github.drawjustin.kairos.user.entity.User
import io.github.drawjustin.kairos.user.entity.UserRole
import io.github.drawjustin.kairos.user.repository.UserRepository
import java.security.SecureRandom
import java.util.Base64
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
// tenant, project, API key의 최소 관리 흐름을 한곳에서 묶는다.
class PlatformManagementService(
    private val tenantRepository: TenantRepository,
    private val projectRepository: ProjectRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val userRepository: UserRepository,
    private val tokenHasher: TokenHasher,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun createTenant(principal: AuthenticatedUser, request: CreateTenantRequest): TenantOutput {
        // tenant는 플랫폼 운영 단위이므로 일반 사용자가 임의 생성하지 못하게 제한한다.
        if (principal.role != UserRole.ADMIN) {
            throw KairosException(KairosErrorCode.TENANT_CREATION_FORBIDDEN)
        }

        val name = request.name.trim()
        // tenant 이름은 플랫폼 전체에서 하나만 쓰게 해 운영 식별자를 단순하게 유지한다.
        if (tenantRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(name)) {
            throw KairosException(KairosErrorCode.TENANT_ALREADY_EXISTS)
        }

        val owner = principal.toActiveUser()
        val tenant = tenantRepository.save(
            Tenant(
                ownerUser = owner,
                name = name,
            ),
        )

        return tenant.toOutput()
    }

    @Transactional(readOnly = true)
    fun listTenants(principal: AuthenticatedUser): List<TenantOutput> =
        tenantRepository.findAllByOwnerUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(principal.id)
            .map { it.toOutput() }

    @Transactional
    fun createProject(
        principal: AuthenticatedUser,
        tenantId: Long,
        request: CreateProjectRequest,
    ): ProjectOutput {
        // project 생성 전에는 tenant 소유권부터 확인해 다른 사용자의 공간에 못 쓰게 한다.
        val tenant = resolveOwnedTenant(principal, tenantId)
        val name = request.name.trim()
        if (projectRepository.existsByTenant_IdAndNameIgnoreCaseAndDeletedAtIsNull(tenantId, name)) {
            throw KairosException(KairosErrorCode.PROJECT_ALREADY_EXISTS)
        }

        val project = projectRepository.save(
            Project(
                tenant = tenant,
                name = name,
                environment = request.environment,
            ),
        )

        return project.toOutput()
    }

    @Transactional(readOnly = true)
    fun listProjects(principal: AuthenticatedUser, tenantId: Long): List<ProjectOutput> {
        resolveOwnedTenant(principal, tenantId)
        return projectRepository.findAllByTenant_IdAndDeletedAtIsNullOrderByCreatedAtAsc(tenantId)
            .map { it.toOutput() }
    }

    @Transactional
    fun createApiKey(
        principal: AuthenticatedUser,
        projectId: Long,
        request: CreateApiKeyRequest,
    ): ApiKeyIssueOutput {
        // API key 발급 주체를 남겨 이후 감사 로그나 운영 이력 추적에 쓸 수 있게 한다.
        val creator = principal.toActiveUser()
        val project = resolveOwnedProject(principal, projectId)
        val name = request.name.trim()
        if (apiKeyRepository.existsByProject_IdAndNameIgnoreCaseAndDeletedAtIsNull(projectId, name)) {
            throw KairosException(KairosErrorCode.API_KEY_ALREADY_EXISTS)
        }

        // 원문 key는 응답 시점에만 보여주고 DB에는 prefix와 hash만 남긴다.
        val rawKey = generateApiKey()
        val keyPrefix = rawKey.take(KEY_PREVIEW_LENGTH)
        val apiKey = apiKeyRepository.save(
            ApiKey(
                project = project,
                createdByUser = creator,
                name = name,
                keyPrefix = keyPrefix,
                keyHash = tokenHasher.hash(rawKey),
                expiresAt = request.expiresAt,
            ),
        )

        return ApiKeyIssueOutput(
            apiKey = rawKey,
            key = apiKey.toOutput(),
        )
    }

    @Transactional(readOnly = true)
    fun listApiKeys(principal: AuthenticatedUser, projectId: Long): List<ApiKeyOutput> {
        resolveOwnedProject(principal, projectId)
        return apiKeyRepository.findAllByProject_IdAndDeletedAtIsNullOrderByCreatedAtAsc(projectId)
            .map { it.toOutput() }
    }

    private fun resolveOwnedTenant(principal: AuthenticatedUser, tenantId: Long): Tenant {
        // 현재는 owner 또는 ADMIN만 tenant를 볼 수 있게 단순한 접근 규칙을 둔다.
        val tenant = tenantRepository.findByIdAndDeletedAtIsNull(tenantId)
            .orElseThrow { KairosException(KairosErrorCode.TENANT_NOT_FOUND) }
        val ownerUserId = requireNotNull(tenant.ownerUser.id) { "Tenant owner id must exist" }
        if (principal.role != io.github.drawjustin.kairos.user.entity.UserRole.ADMIN && ownerUserId != principal.id) {
            throw KairosException(KairosErrorCode.TENANT_ACCESS_DENIED)
        }
        return tenant
    }

    private fun resolveOwnedProject(principal: AuthenticatedUser, projectId: Long): Project {
        // project 권한은 상위 tenant owner 기준으로 판정해 정책을 한곳에 모은다.
        val project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow { KairosException(KairosErrorCode.PROJECT_NOT_FOUND) }
        val ownerUserId = requireNotNull(project.tenant.ownerUser.id) { "Tenant owner id must exist" }
        if (principal.role != io.github.drawjustin.kairos.user.entity.UserRole.ADMIN && ownerUserId != principal.id) {
            throw KairosException(KairosErrorCode.PROJECT_ACCESS_DENIED)
        }
        return project
    }

    private fun AuthenticatedUser.toActiveUser(): User =
        // JWT 안의 사용자도 soft delete 이후에는 운영 리소스를 만들 수 없게 막는다.
        userRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow { KairosException(KairosErrorCode.USER_NOT_FOUND) }

    private fun Tenant.toOutput(): TenantOutput = TenantOutput(
        id = requireNotNull(id) { "Tenant id must exist" },
        name = name,
        status = status,
        ownerUserId = requireNotNull(ownerUser.id) { "Tenant owner id must exist" },
        createdAt = requireNotNull(createdAt) { "Tenant createdAt must exist" },
    )

    private fun Project.toOutput(): ProjectOutput = ProjectOutput(
        id = requireNotNull(id) { "Project id must exist" },
        tenantId = requireNotNull(tenant.id) { "Project tenant id must exist" },
        name = name,
        environment = environment,
        status = status,
        createdAt = requireNotNull(createdAt) { "Project createdAt must exist" },
    )

    private fun ApiKey.toOutput(): ApiKeyOutput = ApiKeyOutput(
        id = requireNotNull(id) { "ApiKey id must exist" },
        projectId = requireNotNull(project.id) { "ApiKey project id must exist" },
        name = name,
        keyPreview = "$keyPrefix...",
        expiresAt = expiresAt,
        lastUsedAt = lastUsedAt,
        revokedAt = revokedAt,
        createdAt = requireNotNull(createdAt) { "ApiKey createdAt must exist" },
    )

    private fun generateApiKey(): String {
        // URL-safe base64를 써서 헤더/환경변수/로그 마스킹 처리와 함께 쓰기 쉽게 만든다.
        val randomBytes = ByteArray(24)
        secureRandom.nextBytes(randomBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
        return "kairos_sk_$token"
    }

    companion object {
        private const val KEY_PREVIEW_LENGTH = 18
    }
}
