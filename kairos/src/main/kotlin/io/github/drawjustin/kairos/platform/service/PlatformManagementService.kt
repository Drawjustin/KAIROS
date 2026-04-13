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
import io.github.drawjustin.kairos.platform.dto.CreateTenantUserRequest
import io.github.drawjustin.kairos.platform.dto.ProjectOutput
import io.github.drawjustin.kairos.platform.dto.TenantOutput
import io.github.drawjustin.kairos.platform.dto.TenantUserOutput
import io.github.drawjustin.kairos.platform.dto.UpdateTenantUserRoleRequest
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.entity.Tenant
import io.github.drawjustin.kairos.tenant.entity.TenantUser
import io.github.drawjustin.kairos.tenant.entity.TenantUserRole
import io.github.drawjustin.kairos.tenant.repository.TenantRepository
import io.github.drawjustin.kairos.tenant.repository.TenantUserRepository
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
    private val tenantUserRepository: TenantUserRepository,
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

        val creator = principal.toActiveUser()
        val tenant = tenantRepository.save(
            Tenant(
                name = name,
            ),
        )
        // tenant를 만든 사용자에게 첫 OWNER 역할을 부여해 이후 관리 권한을 연결한다.
        tenantUserRepository.save(
            TenantUser(
                tenant = tenant,
                user = creator,
                role = TenantUserRole.OWNER,
            ),
        )

        return tenant.toOutput()
    }

    @Transactional(readOnly = true)
    fun listTenants(principal: AuthenticatedUser): List<TenantOutput> {
        // tenant 전체 목록은 운영자 백오피스 용도로만 노출한다.
        if (principal.role != UserRole.ADMIN) {
            throw KairosException(KairosErrorCode.TENANT_ACCESS_DENIED)
        }

        return tenantRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()
            .map { it.toOutput() }
    }

    @Transactional(readOnly = true)
    fun listTenantUsers(principal: AuthenticatedUser, tenantId: Long): List<TenantUserOutput> {
        // tenant 사용자 목록은 OWNER/ADMIN 또는 플랫폼 ADMIN만 조회할 수 있다.
        resolveTenantForRole(principal, tenantId, managerRoles())
        return tenantUserRepository.findAllByTenant_IdAndDeletedAtIsNullOrderByCreatedAtAsc(tenantId)
            .map { it.toOutput() }
    }

    @Transactional
    fun createTenantUser(
        principal: AuthenticatedUser,
        tenantId: Long,
        request: CreateTenantUserRequest,
    ): TenantUserOutput {
        // 멤버 초대와 역할 부여는 tenant OWNER 또는 플랫폼 ADMIN만 처리하게 둔다.
        val tenant = tenantRepository.findByIdAndDeletedAtIsNull(tenantId)
            .orElseThrow { KairosException(KairosErrorCode.TENANT_NOT_FOUND) }
        requireTenantOwner(principal, tenantId)
        val user = userRepository.findByEmailAndDeletedAtIsNull(request.email.trim())
            .orElseThrow { KairosException(KairosErrorCode.USER_NOT_FOUND) }
        if (tenantUserRepository.existsByTenant_IdAndUser_IdAndDeletedAtIsNull(tenantId, requireNotNull(user.id))) {
            throw KairosException(KairosErrorCode.TENANT_USER_ALREADY_EXISTS)
        }

        val tenantUser = tenantUserRepository.save(
            TenantUser(
                tenant = tenant,
                user = user,
                role = request.role,
            ),
        )
        return tenantUser.toOutput()
    }

    @Transactional
    fun updateTenantUserRole(
        principal: AuthenticatedUser,
        tenantUserId: Long,
        request: UpdateTenantUserRoleRequest,
    ): TenantUserOutput {
        val tenantUser = tenantUserRepository.findByIdAndDeletedAtIsNull(tenantUserId)
            .orElseThrow { KairosException(KairosErrorCode.TENANT_USER_NOT_FOUND) }
        val tenantId = requireNotNull(tenantUser.tenant.id) { "Tenant user tenant id must exist" }
        requireTenantOwner(principal, tenantId)

        if (tenantUser.role == TenantUserRole.OWNER && request.role != TenantUserRole.OWNER) {
            validateLastOwner(tenantId)
        }

        tenantUser.role = request.role
        return tenantUser.toOutput()
    }

    @Transactional
    fun deleteTenantUser(principal: AuthenticatedUser, tenantUserId: Long) {
        val tenantUser = tenantUserRepository.findByIdAndDeletedAtIsNull(tenantUserId)
            .orElseThrow { KairosException(KairosErrorCode.TENANT_USER_NOT_FOUND) }
        val tenantId = requireNotNull(tenantUser.tenant.id) { "Tenant user tenant id must exist" }
        requireTenantOwner(principal, tenantId)

        if (tenantUser.role == TenantUserRole.OWNER) {
            validateLastOwner(tenantId)
        }

        tenantUserRepository.delete(tenantUser)
    }

    @Transactional
    fun createProject(
        principal: AuthenticatedUser,
        tenantId: Long,
        request: CreateProjectRequest,
    ): ProjectOutput {
        // project 생성은 tenant의 OWNER/ADMIN 또는 플랫폼 ADMIN으로 제한한다.
        val tenant = resolveTenantForRole(principal, tenantId, managerRoles())
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
        resolveTenantForRole(principal, tenantId, managerRoles())
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
        val project = resolveManagedProject(principal, projectId)
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
        resolveManagedProject(principal, projectId)
        return apiKeyRepository.findAllByProject_IdAndDeletedAtIsNullOrderByCreatedAtAsc(projectId)
            .map { it.toOutput() }
    }

    private fun resolveTenantForRole(
        principal: AuthenticatedUser,
        tenantId: Long,
        allowedRoles: Collection<TenantUserRole>,
    ): Tenant {
        // 플랫폼 ADMIN은 모든 tenant를 다루고, 일반 사용자는 tenant_user 역할 기준으로만 접근한다.
        val tenant = tenantRepository.findByIdAndDeletedAtIsNull(tenantId)
            .orElseThrow { KairosException(KairosErrorCode.TENANT_NOT_FOUND) }
        if (principal.role != UserRole.ADMIN &&
            !tenantUserRepository.existsByTenant_IdAndUser_IdAndRoleInAndDeletedAtIsNull(
                tenantId = tenantId,
                userId = principal.id,
                roles = allowedRoles,
            )
        ) {
            throw KairosException(KairosErrorCode.TENANT_ACCESS_DENIED)
        }
        return tenant
    }

    private fun resolveManagedProject(principal: AuthenticatedUser, projectId: Long): Project {
        // project 권한도 상위 tenant의 OWNER/ADMIN 정책을 그대로 따른다.
        val project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow { KairosException(KairosErrorCode.PROJECT_NOT_FOUND) }
        val tenantId = requireNotNull(project.tenant.id) { "Project tenant id must exist" }
        if (principal.role != UserRole.ADMIN &&
            !tenantUserRepository.existsByTenant_IdAndUser_IdAndRoleInAndDeletedAtIsNull(
                tenantId = tenantId,
                userId = principal.id,
                roles = managerRoles(),
            )
        ) {
            throw KairosException(KairosErrorCode.PROJECT_ACCESS_DENIED)
        }
        return project
    }

    private fun requireTenantOwner(principal: AuthenticatedUser, tenantId: Long) {
        if (principal.role == UserRole.ADMIN) {
            return
        }
        if (!tenantUserRepository.existsByTenant_IdAndUser_IdAndRoleInAndDeletedAtIsNull(
                tenantId = tenantId,
                userId = principal.id,
                roles = ownerRoles(),
            )
        ) {
            throw KairosException(KairosErrorCode.TENANT_USER_MANAGEMENT_FORBIDDEN)
        }
    }

    private fun validateLastOwner(tenantId: Long) {
        if (tenantUserRepository.countByTenant_IdAndRoleAndDeletedAtIsNull(tenantId, TenantUserRole.OWNER) <= 1L) {
            throw KairosException(KairosErrorCode.TENANT_LAST_OWNER_REQUIRED)
        }
    }

    private fun AuthenticatedUser.toActiveUser(): User =
        // JWT 안의 사용자도 soft delete 이후에는 운영 리소스를 만들 수 없게 막는다.
        userRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow { KairosException(KairosErrorCode.USER_NOT_FOUND) }

    private fun Tenant.toOutput(): TenantOutput = TenantOutput(
        id = requireNotNull(id) { "Tenant id must exist" },
        name = name,
        status = status,
        createdAt = requireNotNull(createdAt) { "Tenant createdAt must exist" },
    )

    private fun TenantUser.toOutput(): TenantUserOutput = TenantUserOutput(
        id = requireNotNull(id) { "TenantUser id must exist" },
        tenantId = requireNotNull(tenant.id) { "TenantUser tenant id must exist" },
        userId = requireNotNull(user.id) { "TenantUser user id must exist" },
        email = user.email,
        role = role,
        createdAt = requireNotNull(createdAt) { "TenantUser createdAt must exist" },
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

        private fun ownerRoles() = listOf(TenantUserRole.OWNER)

        private fun managerRoles() = listOf(TenantUserRole.OWNER, TenantUserRole.ADMIN)
    }
}
