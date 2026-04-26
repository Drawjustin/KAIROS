package io.github.drawjustin.kairos.ai

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.ai.repository.AiUsageLogRepository
import io.github.drawjustin.kairos.ai.dto.AiModel
import io.github.drawjustin.kairos.ai.type.AiUsageStatus
import io.github.drawjustin.kairos.ai.dto.ChatChoiceResponse
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.dto.ChatMessageResponse
import io.github.drawjustin.kairos.ai.dto.ChatUsageResponse
import io.github.drawjustin.kairos.ai.provider.ProviderAdapter
import io.github.drawjustin.kairos.ai.provider.ProviderRouter
import io.github.drawjustin.kairos.auth.dto.AuthOutput
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.platform.dto.ApiKeyIssueResponse
import io.github.drawjustin.kairos.platform.dto.CreateApiKeyRequest
import io.github.drawjustin.kairos.platform.dto.CreateProjectRequest
import io.github.drawjustin.kairos.platform.dto.CreateTenantRequest
import io.github.drawjustin.kairos.platform.dto.ProjectResponse
import io.github.drawjustin.kairos.platform.dto.TenantResponse
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.repository.TenantRepository
import io.github.drawjustin.kairos.tenant.repository.TenantUserRepository
import io.github.drawjustin.kairos.user.repository.UserRepository
import io.github.drawjustin.kairos.user.type.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
// Unified AI API가 API key 인증과 provider adapter 연결을 제대로 타는지 검증한다.
class UnifiedAiIntegrationTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var tenantRepository: TenantRepository

    @Autowired
    lateinit var tenantUserRepository: TenantUserRepository

    @Autowired
    lateinit var projectRepository: ProjectRepository

    @Autowired
    lateinit var aiUsageLogRepository: AiUsageLogRepository

    @MockitoBean
    lateinit var providerRouter: ProviderRouter

    lateinit var providerAdapter: ProviderAdapter

    @BeforeEach
    fun setUp() {
        // soft delete된 row까지 포함해 초기 상태를 맞춰 API key 인증 테스트가 흔들리지 않게 한다.
        jdbcTemplate.execute(
            "truncate table ai_usage_log, api_key, project, tenant_user, tenant, refresh_session, users restart identity cascade",
        )
        providerAdapter = mock(ProviderAdapter::class.java)
    }

    @Test
    fun `chat completions returns unified response for valid api key`() {
        val adminLogin = registerAdminAndLogin("ai-admin@example.com")
        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "ai-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "ai-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default"))
        val request = ChatCompletionRequest(
            model = AiModel.GPT_4O_MINI,
            messages = listOf(
                ChatMessageRequest(
                    role = "user",
                    content = "안녕",
                ),
            ),
        )

        val enrichedRequest = request.copy(
            messages = listOf(
                ChatMessageRequest(
                    role = "system",
                    content = "너는 KAIROS의 사내 AI 어시스턴트다. 내부 문서를 우선 참고하고, 근거 없는 내용은 추측하지 마라.",
                ),
            ) + request.messages,
        )

        given(providerRouter.route(AiModel.GPT_4O_MINI)).willReturn(providerAdapter)
        given(providerAdapter.chatCompletion(enrichedRequest))
            .willReturn(
                ChatCompletionResponse(
                    id = "chatcmpl_test_123",
                    `object` = "chat.completion",
                    created = 1_713_086_400,
                    model = "gpt-4o-mini",
                    choices = listOf(
                        ChatChoiceResponse(
                            index = 0,
                            message = ChatMessageResponse(
                                role = "assistant",
                                content = "안녕하세요. 무엇을 도와드릴까요?",
                            ),
                            finishReason = "stop",
                        ),
                    ),
                    usage = ChatUsageResponse(
                        promptTokens = 10,
                        completionTokens = 12,
                        totalTokens = 22,
                    ),
                ),
            )

        val result = mockMvc.perform(
            post("/api/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${issuedKey.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = objectMapper.readValue(result.response.contentAsByteArray, ChatCompletionResponse::class.java)
        assertThat(response.id).isEqualTo("chatcmpl_test_123")
        assertThat(response.choices.single().message.content).isEqualTo("안녕하세요. 무엇을 도와드릴까요?")
        assertThat(response.usage?.totalTokens).isEqualTo(22)

        val usageLog = aiUsageLogRepository.findAll().single()
        assertThat(usageLog.project.id).isEqualTo(project.id)
        assertThat(usageLog.apiKey.id).isEqualTo(issuedKey.key.id)
        assertThat(usageLog.provider).isEqualTo("OPENAI")
        assertThat(usageLog.model).isEqualTo("gpt-4o-mini")
        assertThat(usageLog.inputTokens).isEqualTo(10)
        assertThat(usageLog.outputTokens).isEqualTo(12)
        assertThat(usageLog.totalTokens).isEqualTo(22)
        assertThat(usageLog.status).isEqualTo(AiUsageStatus.SUCCESS)
        assertThat(usageLog.providerResponseId).isEqualTo("chatcmpl_test_123")
    }

    @Test
    fun `chat completions rejects invalid api key`() {
        mockMvc.perform(
            post("/api/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer kairos_sk_invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        ChatCompletionRequest(
                            model = AiModel.GPT_4O_MINI,
                            messages = listOf(
                                ChatMessageRequest(
                                    role = "user",
                                    content = "안녕",
                                ),
                            ),
                        ),
                    ),
                ),
        )
            .andExpect(status().isUnauthorized)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("AI_001")
            }
    }

    @Test
    fun `chat completions records failed usage log when provider fails`() {
        val adminLogin = registerAdminAndLogin("ai-provider-failure-admin@example.com")
        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "ai-failure-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "ai-failure-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default"))
        val request = ChatCompletionRequest(
            model = AiModel.GPT_4O_MINI,
            messages = listOf(
                ChatMessageRequest(
                    role = "user",
                    content = "안녕",
                ),
            ),
        )
        val enrichedRequest = request.copy(
            messages = listOf(
                ChatMessageRequest(
                    role = "system",
                    content = "너는 KAIROS의 사내 AI 어시스턴트다. 내부 문서를 우선 참고하고, 근거 없는 내용은 추측하지 마라.",
                ),
            ) + request.messages,
        )

        given(providerRouter.route(AiModel.GPT_4O_MINI)).willReturn(providerAdapter)
        given(providerAdapter.chatCompletion(enrichedRequest))
            .willThrow(KairosException(KairosErrorCode.AI_PROVIDER_ERROR))

        mockMvc.perform(
            post("/api/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${issuedKey.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isInternalServerError)

        val usageLog = aiUsageLogRepository.findAll().single()
        assertThat(usageLog.project.id).isEqualTo(project.id)
        assertThat(usageLog.apiKey.id).isEqualTo(issuedKey.key.id)
        assertThat(usageLog.provider).isEqualTo("OPENAI")
        assertThat(usageLog.model).isEqualTo("gpt-4o-mini")
        assertThat(usageLog.status).isEqualTo(AiUsageStatus.FAILED)
        assertThat(usageLog.errorCode).isEqualTo("AI_006")
        assertThat(usageLog.totalTokens).isZero()
    }

    private fun registerAdminAndLogin(email: String): AuthOutput {
        register(RegisterRequest(email = email, password = "password123"))
        val adminUser = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow()
        adminUser.role = UserRole.ADMIN
        userRepository.save(adminUser)
        return login(LoginRequest(email = email, password = "password123"))
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

    private fun createProject(accessToken: String, tenantId: Long, request: CreateProjectRequest) =
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

    private fun createApiKey(accessToken: String, projectId: Long, request: CreateApiKeyRequest) =
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
}
