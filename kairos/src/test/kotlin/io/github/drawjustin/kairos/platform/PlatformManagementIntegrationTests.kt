package io.github.drawjustin.kairos.platform

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.ai.entity.AiUsageLog
import io.github.drawjustin.kairos.ai.entity.AiUsageStatus
import io.github.drawjustin.kairos.ai.repository.AiUsageLogRepository
import io.github.drawjustin.kairos.apikey.repository.ApiKeyRepository
import io.github.drawjustin.kairos.auth.dto.AuthOutput
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.auth.repository.RefreshSessionRepository
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.platform.dto.ApiKeyIssueResponse
import io.github.drawjustin.kairos.platform.dto.ApiKeysResponse
import io.github.drawjustin.kairos.platform.dto.CreateApiKeyRequest
import io.github.drawjustin.kairos.platform.dto.CreateProjectRequest
import io.github.drawjustin.kairos.platform.dto.CreateTenantRequest
import io.github.drawjustin.kairos.platform.dto.CreateTenantUserRequest
import io.github.drawjustin.kairos.platform.dto.ProjectAiUsageSummaryResponse
import io.github.drawjustin.kairos.platform.dto.ProjectResponse
import io.github.drawjustin.kairos.platform.dto.ProjectsResponse
import io.github.drawjustin.kairos.platform.dto.TenantResponse
import io.github.drawjustin.kairos.platform.dto.TenantUserResponse
import io.github.drawjustin.kairos.platform.dto.TenantUsersResponse
import io.github.drawjustin.kairos.platform.dto.TenantsResponse
import io.github.drawjustin.kairos.platform.dto.UpdateTenantUserRoleRequest
import io.github.drawjustin.kairos.project.entity.ProjectEnvironment
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.entity.TenantUserRole
import io.github.drawjustin.kairos.tenant.repository.TenantRepository
import io.github.drawjustin.kairos.tenant.repository.TenantUserRepository
import io.github.drawjustin.kairos.user.entity.UserRole
import io.github.drawjustin.kairos.user.repository.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
// tenant -> project -> API key 생성 흐름과 tenant membership 권한 검증을 함께 확인한다.
class PlatformManagementIntegrationTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var tenantRepository: TenantRepository

    @Autowired
    lateinit var tenantUserRepository: TenantUserRepository

    @Autowired
    lateinit var projectRepository: ProjectRepository

    @Autowired
    lateinit var apiKeyRepository: ApiKeyRepository

    @Autowired
    lateinit var aiUsageLogRepository: AiUsageLogRepository

    @Autowired
    lateinit var refreshSessionRepository: RefreshSessionRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // soft delete된 row까지 포함해 완전히 비워야 다음 테스트의 FK와 unique 제약이 흔들리지 않는다.
        jdbcTemplate.execute(
            "truncate table ai_usage_log, api_key, project, tenant_user, tenant, refresh_session, users restart identity cascade",
        )
    }

    @Test
    fun `admin can create tenant project and api key`() {
        register(
            RegisterRequest(
                email = "admin@example.com",
                password = "password123",
            ),
        )
        val adminUser = userRepository.findByEmailAndDeletedAtIsNull("admin@example.com").orElseThrow()
        adminUser.role = UserRole.ADMIN
        userRepository.save(adminUser)

        val loginResponse = login(
            LoginRequest(
                email = "admin@example.com",
                password = "password123",
            ),
        )

        val tenant = createTenant(loginResponse.accessToken, CreateTenantRequest(name = "search-team"))
        assertThat(tenant.name).isEqualTo("search-team")

        register(
            RegisterRequest(
                email = "admin2@example.com",
                password = "password123",
            ),
        )
        val anotherAdmin = userRepository.findByEmailAndDeletedAtIsNull("admin2@example.com").orElseThrow()
        anotherAdmin.role = UserRole.ADMIN
        userRepository.save(anotherAdmin)
        val anotherAdminLogin = login(
            LoginRequest(
                email = "admin2@example.com",
                password = "password123",
            ),
        )
        createTenant(anotherAdminLogin.accessToken, CreateTenantRequest(name = "chatbot-team"))

        val tenants = listTenants(loginResponse.accessToken)
        assertThat(tenants).hasSize(2)
        assertThat(tenants).extracting<String> { it.name }.containsExactly("search-team", "chatbot-team")

        val project = createProject(
            loginResponse.accessToken,
            tenant.id,
            CreateProjectRequest(
                name = "chatbot-prod",
                environment = ProjectEnvironment.OPER,
            ),
        )
        assertThat(project.tenantId).isEqualTo(tenant.id)
        assertThat(project.name).isEqualTo("chatbot-prod")

        val projects = listProjects(loginResponse.accessToken, tenant.id)
        assertThat(projects).extracting<Long> { it.id }.containsExactly(project.id)

        val issuedKey = createApiKey(
            loginResponse.accessToken,
            project.id,
            CreateApiKeyRequest(name = "default-key"),
        )

        // 발급 직후 응답에서만 원문 key를 돌려주고, 이후 저장소에는 hash만 남아야 한다.
        assertThat(issuedKey.apiKey).startsWith("kairos_sk_")
        assertThat(issuedKey.key.projectId).isEqualTo(project.id)
        assertThat(issuedKey.key.keyPreview).startsWith("kairos_sk_")
        assertThat(issuedKey.key.keyPreview).endsWith("...")
        assertThat(issuedKey.key.keyPreview).doesNotContain(issuedKey.apiKey)

        val savedKey = apiKeyRepository.findAllByProject_IdAndDeletedAtIsNullOrderByCreatedAtAsc(project.id).single()
        assertThat(savedKey.keyHash).isNotEqualTo(issuedKey.apiKey)
        assertThat(savedKey.keyPrefix).isEqualTo(issuedKey.apiKey.take(18))

        val listedKeys = listApiKeys(loginResponse.accessToken, project.id)
        assertThat(listedKeys).hasSize(1)
        assertThat(listedKeys.first().keyPreview).isEqualTo(issuedKey.key.keyPreview)
    }

    @Test
    fun `non admin cannot create tenant`() {
        register(
            RegisterRequest(
                email = "user@example.com",
                password = "password123",
            ),
        )
        val loginResponse = login(
            LoginRequest(
                email = "user@example.com",
                password = "password123",
            ),
        )

        mockMvc.perform(
            post("/api/admin/platform/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${loginResponse.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(CreateTenantRequest(name = "blocked-tenant"))),
        )
            .andExpect(status().isForbidden)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("TENANT_000")
            }
    }

    @Test
    fun `non admin cannot list tenants`() {
        register(
            RegisterRequest(
                email = "viewer@example.com",
                password = "password123",
            ),
        )
        val loginResponse = login(
            LoginRequest(
                email = "viewer@example.com",
                password = "password123",
            ),
        )

        mockMvc.perform(
            get("/api/admin/platform/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${loginResponse.accessToken}"),
        )
            .andExpect(status().isForbidden)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("TENANT_003")
            }
    }

    @Test
    fun `non member cannot manage another tenants resources`() {
        register(
            RegisterRequest(
                email = "tenant-admin@example.com",
                password = "password123",
            ),
        )
        val tenantAdminUser = userRepository.findByEmailAndDeletedAtIsNull("tenant-admin@example.com").orElseThrow()
        tenantAdminUser.role = UserRole.ADMIN
        userRepository.save(tenantAdminUser)

        val tenantAdminLogin = login(
            LoginRequest(
                email = "tenant-admin@example.com",
                password = "password123",
            ),
        )
        val tenant = createTenant(tenantAdminLogin.accessToken, CreateTenantRequest(name = "search-team-2"))
        val project = createProject(
            tenantAdminLogin.accessToken,
            tenant.id,
            CreateProjectRequest(name = "secret-prod"),
        )

        register(
            RegisterRequest(
                email = "other@example.com",
                password = "password123",
            ),
        )
        val otherLogin = login(
            LoginRequest(
                email = "other@example.com",
                password = "password123",
            ),
        )

        mockMvc.perform(
            post("/api/platform/tenants/${tenant.id}/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${otherLogin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(CreateProjectRequest(name = "hijack"))),
        )
            // tenant에 OWNER/ADMIN으로 속하지 않으면 하위 project 생성도 막혀야 한다.
            .andExpect(status().isForbidden)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("TENANT_003")
            }

        mockMvc.perform(
            get("/api/platform/projects/${project.id}/api-keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${otherLogin.accessToken}"),
        )
            // project 권한도 상위 tenant membership 기준으로 동일하게 제한한다.
            .andExpect(status().isForbidden)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("PROJECT_003")
            }
    }

    @Test
    fun `owner can add update list and delete tenant users`() {
        register(RegisterRequest(email = "platform-admin@example.com", password = "password123"))
        val platformAdmin = userRepository.findByEmailAndDeletedAtIsNull("platform-admin@example.com").orElseThrow()
        platformAdmin.role = UserRole.ADMIN
        userRepository.save(platformAdmin)
        val platformAdminLogin = login(LoginRequest(email = "platform-admin@example.com", password = "password123"))

        register(RegisterRequest(email = "member1@example.com", password = "password123"))
        register(RegisterRequest(email = "member2@example.com", password = "password123"))

        val tenant = createTenant(platformAdminLogin.accessToken, CreateTenantRequest(name = "tenant-user-team"))
        val addedUser = createTenantUser(
            platformAdminLogin.accessToken,
            tenant.id,
            CreateTenantUserRequest(email = "member1@example.com", role = TenantUserRole.MEMBER),
        )
        assertThat(addedUser.email).isEqualTo("member1@example.com")
        assertThat(addedUser.role).isEqualTo(TenantUserRole.MEMBER)

        val listedUsers = listTenantUsers(platformAdminLogin.accessToken, tenant.id)
        assertThat(listedUsers.map { it.email })
            .contains("platform-admin@example.com", "member1@example.com")

        val updatedUser = updateTenantUserRole(
            platformAdminLogin.accessToken,
            addedUser.id,
            UpdateTenantUserRoleRequest(role = TenantUserRole.ADMIN),
        )
        assertThat(updatedUser.role).isEqualTo(TenantUserRole.ADMIN)

        val anotherUser = createTenantUser(
            platformAdminLogin.accessToken,
            tenant.id,
            CreateTenantUserRequest(email = "member2@example.com", role = TenantUserRole.MEMBER),
        )
        deleteTenantUser(platformAdminLogin.accessToken, anotherUser.id)

        val remainingUsers = listTenantUsers(platformAdminLogin.accessToken, tenant.id)
        assertThat(remainingUsers.map { it.email })
            .contains("platform-admin@example.com", "member1@example.com")
            .doesNotContain("member2@example.com")
    }

    @Test
    fun `tenant admin can list users but cannot manage them`() {
        register(RegisterRequest(email = "platform-admin2@example.com", password = "password123"))
        val platformAdmin = userRepository.findByEmailAndDeletedAtIsNull("platform-admin2@example.com").orElseThrow()
        platformAdmin.role = UserRole.ADMIN
        userRepository.save(platformAdmin)
        val platformAdminLogin = login(LoginRequest(email = "platform-admin2@example.com", password = "password123"))

        register(RegisterRequest(email = "tenant-operator@example.com", password = "password123"))
        register(RegisterRequest(email = "tenant-member@example.com", password = "password123"))

        val tenant = createTenant(platformAdminLogin.accessToken, CreateTenantRequest(name = "tenant-user-policy-team"))
        val tenantAdminMembership = createTenantUser(
            platformAdminLogin.accessToken,
            tenant.id,
            CreateTenantUserRequest(email = "tenant-operator@example.com", role = TenantUserRole.ADMIN),
        )

        val tenantAdminLogin = login(LoginRequest(email = "tenant-operator@example.com", password = "password123"))

        val listedUsers = listTenantUsers(tenantAdminLogin.accessToken, tenant.id)
        assertThat(listedUsers.map { it.email })
            .contains("platform-admin2@example.com", "tenant-operator@example.com")

        mockMvc.perform(
            post("/api/platform/tenants/${tenant.id}/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantAdminLogin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        CreateTenantUserRequest(email = "tenant-member@example.com", role = TenantUserRole.MEMBER),
                    ),
                ),
        )
            .andExpect(status().isForbidden)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("TENANTUSER_003")
            }

        mockMvc.perform(
            patch("/api/platform/tenant-users/${tenantAdminMembership.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tenantAdminLogin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(UpdateTenantUserRoleRequest(role = TenantUserRole.MEMBER))),
        )
            .andExpect(status().isForbidden)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("TENANTUSER_003")
            }
    }

    @Test
    fun `cannot remove last tenant owner`() {
        register(RegisterRequest(email = "platform-admin3@example.com", password = "password123"))
        val platformAdmin = userRepository.findByEmailAndDeletedAtIsNull("platform-admin3@example.com").orElseThrow()
        platformAdmin.role = UserRole.ADMIN
        userRepository.save(platformAdmin)
        val platformAdminLogin = login(LoginRequest(email = "platform-admin3@example.com", password = "password123"))

        val tenant = createTenant(platformAdminLogin.accessToken, CreateTenantRequest(name = "last-owner-team"))
        val ownerMembership = listTenantUsers(platformAdminLogin.accessToken, tenant.id)
            .single { it.email == "platform-admin3@example.com" }

        mockMvc.perform(
            patch("/api/platform/tenant-users/${ownerMembership.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${platformAdminLogin.accessToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(UpdateTenantUserRoleRequest(role = TenantUserRole.ADMIN))),
        )
            .andExpect(status().isConflict)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("TENANTUSER_004")
            }

        mockMvc.perform(
            delete("/api/platform/tenant-users/${ownerMembership.id}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${platformAdminLogin.accessToken}"),
        )
            .andExpect(status().isConflict)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("TENANTUSER_004")
            }
    }

    @Test
    fun `tenant manager can view project ai usage summary`() {
        register(RegisterRequest(email = "usage-admin@example.com", password = "password123"))
        val adminUser = userRepository.findByEmailAndDeletedAtIsNull("usage-admin@example.com").orElseThrow()
        adminUser.role = UserRole.ADMIN
        userRepository.save(adminUser)
        val adminLogin = login(LoginRequest(email = "usage-admin@example.com", password = "password123"))

        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "usage-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "usage-project"))
        val otherProject = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "other-usage-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default-key"))
        val otherIssuedKey = createApiKey(adminLogin.accessToken, otherProject.id, CreateApiKeyRequest(name = "other-key"))
        val projectEntity = projectRepository.findByIdAndDeletedAtIsNull(project.id).orElseThrow()
        val otherProjectEntity = projectRepository.findByIdAndDeletedAtIsNull(otherProject.id).orElseThrow()
        val apiKey = apiKeyRepository.findById(issuedKey.key.id).orElseThrow()
        val otherApiKey = apiKeyRepository.findById(otherIssuedKey.key.id).orElseThrow()

        aiUsageLogRepository.saveAll(
            listOf(
                AiUsageLog(
                    project = projectEntity,
                    apiKey = apiKey,
                    provider = "OPENAI",
                    model = "gpt-4o-mini",
                    inputTokens = 100,
                    outputTokens = 50,
                    totalTokens = 150,
                    status = AiUsageStatus.SUCCESS,
                    latencyMs = 120,
                ),
                AiUsageLog(
                    project = projectEntity,
                    apiKey = apiKey,
                    provider = "OPENAI",
                    model = "gpt-4o-mini",
                    inputTokens = 200,
                    outputTokens = 70,
                    totalTokens = 270,
                    status = AiUsageStatus.SUCCESS,
                    latencyMs = 150,
                ),
                AiUsageLog(
                    project = projectEntity,
                    apiKey = apiKey,
                    provider = "CLAUDE",
                    model = "claude-sonnet-4-6",
                    status = AiUsageStatus.FAILED,
                    latencyMs = 80,
                    errorCode = "AI_006",
                ),
                AiUsageLog(
                    project = otherProjectEntity,
                    apiKey = otherApiKey,
                    provider = "GEMINI",
                    model = "gemini-2.5-flash",
                    inputTokens = 999,
                    outputTokens = 999,
                    totalTokens = 1_998,
                    status = AiUsageStatus.SUCCESS,
                    latencyMs = 90,
                ),
            ),
        )

        val from = Instant.now().minus(1, ChronoUnit.DAYS)
        val to = Instant.now().plus(1, ChronoUnit.DAYS)
        val result = mockMvc.perform(
            get("/api/platform/projects/${project.id}/ai-usage/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${adminLogin.accessToken}")
                .param("from", from.toString())
                .param("to", to.toString()),
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = objectMapper.readValue(result.response.contentAsByteArray, ProjectAiUsageSummaryResponse::class.java).result
        assertThat(response.projectId).isEqualTo(project.id)
        assertThat(response.totalRequests).isEqualTo(3)
        assertThat(response.successRequests).isEqualTo(2)
        assertThat(response.failedRequests).isEqualTo(1)
        assertThat(response.inputTokens).isEqualTo(300)
        assertThat(response.outputTokens).isEqualTo(120)
        assertThat(response.totalTokens).isEqualTo(420)

        val byModel = response.byModel.associateBy { it.provider to it.model }
        assertThat(byModel["OPENAI" to "gpt-4o-mini"]?.totalRequests).isEqualTo(2)
        assertThat(byModel["OPENAI" to "gpt-4o-mini"]?.totalTokens).isEqualTo(420)
        assertThat(byModel["CLAUDE" to "claude-sonnet-4-6"]?.failedRequests).isEqualTo(1)
        assertThat(byModel).doesNotContainKey("GEMINI" to "gemini-2.5-flash")
    }

    private fun register(request: RegisterRequest): AuthOutput {
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
            )
                .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsByteArray, AuthResponse::class.java).result
    }

    private fun login(request: LoginRequest): AuthOutput {
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsByteArray, AuthResponse::class.java).result
    }

    private fun createTenant(accessToken: String, request: CreateTenantRequest) =
        // 테스트 본문에서는 자원 생성 순서만 보이도록 HTTP 세부는 헬퍼에 모은다.
        objectMapper.readValue(
            mockMvc.perform(
                post("/api/admin/platform/tenants")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            TenantResponse::class.java,
        ).result

    private fun listTenants(accessToken: String) =
        objectMapper.readValue(
            mockMvc.perform(
                get("/api/admin/platform/tenants")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            TenantsResponse::class.java,
        ).result

    private fun createTenantUser(accessToken: String, tenantId: Long, request: CreateTenantUserRequest) =
        objectMapper.readValue(
            mockMvc.perform(
                post("/api/platform/tenants/$tenantId/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            TenantUserResponse::class.java,
        ).result

    private fun listTenantUsers(accessToken: String, tenantId: Long) =
        objectMapper.readValue(
            mockMvc.perform(
                get("/api/platform/tenants/$tenantId/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            TenantUsersResponse::class.java,
        ).result

    private fun updateTenantUserRole(accessToken: String, tenantUserId: Long, request: UpdateTenantUserRoleRequest) =
        objectMapper.readValue(
            mockMvc.perform(
                patch("/api/platform/tenant-users/$tenantUserId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            TenantUserResponse::class.java,
        ).result

    private fun deleteTenantUser(accessToken: String, tenantUserId: Long) {
        mockMvc.perform(
            delete("/api/platform/tenant-users/$tenantUserId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
    }

    private fun createProject(accessToken: String, tenantId: Long, request: CreateProjectRequest) =
        // 상위 tenant 경로를 그대로 사용해 실제 라우팅 구조와 동일하게 검증한다.
        objectMapper.readValue(
            mockMvc.perform(
                post("/api/platform/tenants/$tenantId/projects")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            ProjectResponse::class.java,
        ).result

    private fun listProjects(accessToken: String, tenantId: Long) =
        objectMapper.readValue(
            mockMvc.perform(
                get("/api/platform/tenants/$tenantId/projects")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            ProjectsResponse::class.java,
        ).result

    private fun createApiKey(accessToken: String, projectId: Long, request: CreateApiKeyRequest) =
        // API key 생성 응답은 원문 key를 포함하므로 별도 응답 타입으로 읽는다.
        objectMapper.readValue(
            mockMvc.perform(
                post("/api/platform/projects/$projectId/api-keys")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            ApiKeyIssueResponse::class.java,
        ).result

    private fun listApiKeys(accessToken: String, projectId: Long) =
        objectMapper.readValue(
            mockMvc.perform(
                get("/api/platform/projects/$projectId/api-keys")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            ApiKeysResponse::class.java,
        ).result
}
