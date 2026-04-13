package io.github.drawjustin.kairos.platform

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.apikey.repository.ApiKeyRepository
import io.github.drawjustin.kairos.auth.dto.AuthOutput
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.platform.dto.ApiKeyIssueResponse
import io.github.drawjustin.kairos.platform.dto.ApiKeysResponse
import io.github.drawjustin.kairos.platform.dto.CreateApiKeyRequest
import io.github.drawjustin.kairos.platform.dto.CreateProjectRequest
import io.github.drawjustin.kairos.platform.dto.CreateTenantRequest
import io.github.drawjustin.kairos.platform.dto.ProjectResponse
import io.github.drawjustin.kairos.platform.dto.ProjectsResponse
import io.github.drawjustin.kairos.platform.dto.TenantResponse
import io.github.drawjustin.kairos.platform.dto.TenantsResponse
import io.github.drawjustin.kairos.project.entity.ProjectEnvironment
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.repository.TenantRepository
import io.github.drawjustin.kairos.user.entity.UserRole
import io.github.drawjustin.kairos.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
// tenant -> project -> API key 생성 흐름과 소유권 검증을 함께 확인한다.
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
    lateinit var projectRepository: ProjectRepository

    @Autowired
    lateinit var apiKeyRepository: ApiKeyRepository

    @BeforeEach
    fun setUp() {
        // FK 순서를 따라 자식 리소스부터 지워 이전 테스트 데이터 간섭을 막는다.
        apiKeyRepository.deleteAllInBatch()
        projectRepository.deleteAllInBatch()
        tenantRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    @Test
    fun `admin can create tenant project and api key`() {
        register(
            RegisterRequest(
                email = "owner@example.com",
                password = "password123",
            ),
        )
        val ownerUser = userRepository.findByEmailAndDeletedAtIsNull("owner@example.com").orElseThrow()
        ownerUser.role = UserRole.ADMIN
        userRepository.save(ownerUser)

        val loginResponse = login(
            LoginRequest(
                email = "owner@example.com",
                password = "password123",
            ),
        )

        val tenant = createTenant(loginResponse.accessToken, CreateTenantRequest(name = "search-team"))
        assertThat(tenant.name).isEqualTo("search-team")

        val tenants = listTenants(loginResponse.accessToken)
        assertThat(tenants).hasSize(1)
        assertThat(tenants.first().id).isEqualTo(tenant.id)

        val project = createProject(
            loginResponse.accessToken,
            tenant.id,
            CreateProjectRequest(
                name = "chatbot-prod",
                environment = ProjectEnvironment.PRODUCTION,
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
            post("/api/platform/tenants")
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
    fun `non owner cannot manage another tenants resources`() {
        register(
            RegisterRequest(
                email = "owner2@example.com",
                password = "password123",
            ),
        )
        val ownerUser = userRepository.findByEmailAndDeletedAtIsNull("owner2@example.com").orElseThrow()
        ownerUser.role = UserRole.ADMIN
        userRepository.save(ownerUser)

        val ownerLogin = login(
            LoginRequest(
                email = "owner2@example.com",
                password = "password123",
            ),
        )
        val tenant = createTenant(ownerLogin.accessToken, CreateTenantRequest(name = "search-team-2"))
        val project = createProject(
            ownerLogin.accessToken,
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
            // tenant owner가 아니면 하위 project 생성도 막혀야 한다.
            .andExpect(status().isForbidden)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("TENANT_003")
            }

        mockMvc.perform(
            get("/api/platform/projects/${project.id}/api-keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${otherLogin.accessToken}"),
        )
            // project 권한도 상위 tenant owner 기준으로 동일하게 제한한다.
            .andExpect(status().isForbidden)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("PROJECT_003")
            }
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
                post("/api/platform/tenants")
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
                get("/api/platform/tenants")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsByteArray,
            TenantsResponse::class.java,
        ).result

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
