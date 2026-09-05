package io.github.drawjustin.kairos.ai

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.ai.dto.ChatChoiceResponse
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.dto.ChatMessageResponse
import io.github.drawjustin.kairos.ai.dto.ChatUsageResponse
import io.github.drawjustin.kairos.ai.provider.ProviderAdapter
import io.github.drawjustin.kairos.ai.provider.ProviderRouter
import io.github.drawjustin.kairos.ai.repository.AiUsageLogRepository
import io.github.drawjustin.kairos.ai.service.AiToolExecutionContext
import io.github.drawjustin.kairos.ai.service.AiUsageLoggingService
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.github.drawjustin.kairos.apikey.entity.ApiKey
import io.github.drawjustin.kairos.auth.dto.AuthOutput
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.observability.KairosMetrics
import io.github.drawjustin.kairos.platform.dto.ApiKeyIssueResponse
import io.github.drawjustin.kairos.platform.dto.CreateApiKeyRequest
import io.github.drawjustin.kairos.platform.dto.CreateProjectRequest
import io.github.drawjustin.kairos.platform.dto.CreateTenantRequest
import io.github.drawjustin.kairos.platform.dto.ProjectResponse
import io.github.drawjustin.kairos.platform.dto.TenantResponse
import io.github.drawjustin.kairos.user.repository.UserRepository
import io.github.drawjustin.kairos.user.type.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
// 감사 기록을 남기지 못했을 때의 동작은 사고가 아니라 선택이다.
// 성공 경로는 기록 없이 응답을 내보내지 않고(fail-closed),
// 실패 경로는 기록 실패가 원래 실패 원인을 덮지 않게 한다.
class AuditLogFailureTests : IntegrationTestSupport() {
    @TestConfiguration
    class BrokenAuditConfig {
        @Bean
        @Primary
        // 원장 테이블이 응답하지 않는 상황을 재현한다.
        fun brokenAiUsageLoggingService(
            repository: AiUsageLogRepository,
            metrics: KairosMetrics,
        ): AiUsageLoggingService = object : AiUsageLoggingService(repository, metrics) {
            override fun recordSuccess(
                apiKey: ApiKey,
                model: AiModel,
                response: ChatCompletionResponse,
                latencyMs: Long,
                fallbackFromModel: AiModel?,
            ) = throw DataAccessResourceFailureException("usage ledger is unreachable")

            override fun recordFailure(
                apiKey: ApiKey,
                model: AiModel,
                latencyMs: Long,
                errorCode: String,
            ) = throw DataAccessResourceFailureException("usage ledger is unreachable")
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var aiUsageLogRepository: AiUsageLogRepository

    @MockitoBean
    lateinit var providerRouter: ProviderRouter

    private lateinit var providerAdapter: ProviderAdapter
    private lateinit var apiKey: String

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "truncate table pii_detection_log, project_pii_policy, project_budget, context_search_log, " +
                "ai_usage_log, api_key, project_context_source, context_source, project_allowed_model, " +
                "project, tenant_user, tenant, refresh_session, users restart identity cascade",
        )
        providerAdapter = mock(ProviderAdapter::class.java)
        given(providerRouter.route(AiModel.GPT_4O_MINI)).willReturn(providerAdapter)
        apiKey = givenApiKey()
    }

    @Test
    fun `refuses to return a response it could not record`() {
        givenProviderAnswers()

        val result = performChat("안녕하세요")
            .andExpect(status().isInternalServerError)
            .andReturn()

        val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
        // 일반 서버 오류와 섞이면 "원장을 못 남겼다"는 사실이 운영에서 보이지 않는다.
        assertThat(response.errorCode).isEqualTo("AI_015")
        assertThat(aiUsageLogRepository.findAll()).isEmpty()
    }

    @Test
    fun `keeps reporting the real reason when a failed call also fails to record`() {
        // 허용되지 않은 모델이라 provider까지 가지 않고 AI_008로 끝나야 한다.
        jdbcTemplate.update("update project_allowed_model set deleted_at = current_timestamp where deleted_at is null")

        val result = performChat("안녕하세요")
            .andExpect(status().isForbidden)
            .andReturn()

        val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
        // 기록 실패가 원래 원인을 덮으면 진짜 문제를 못 보게 된다.
        assertThat(response.errorCode).isEqualTo("AI_008")
    }

    private fun anyToolList(): List<AiToolDefinition> =
        any(List::class.java) as? List<AiToolDefinition> ?: emptyList()

    private fun givenProviderAnswers() {
        given(
            providerAdapter.chatCompletion(
                any(ChatCompletionRequest::class.java) ?: sampleRequest("안녕하세요"),
                anyToolList(),
                any(AiToolExecutionContext::class.java),
            ),
        )
            .willReturn(
                ChatCompletionResponse(
                    id = "chatcmpl_audit",
                    `object` = "chat.completion",
                    created = 1_713_086_400,
                    model = AiModel.GPT_4O_MINI.value,
                    choices = listOf(
                        ChatChoiceResponse(
                            index = 0,
                            message = ChatMessageResponse(role = ChatRole.ASSISTANT, content = "네"),
                            finishReason = "stop",
                        ),
                    ),
                    usage = ChatUsageResponse(promptTokens = 5, completionTokens = 5, totalTokens = 10),
                ),
            )
    }

    private fun sampleRequest(content: String) = ChatCompletionRequest(
        model = AiModel.GPT_4O_MINI,
        messages = listOf(ChatMessageRequest(role = ChatRole.USER, content = content)),
    )

    private fun performChat(content: String) =
        mockMvc.perform(
            post("/api/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(sampleRequest(content))),
        )

    private fun givenApiKey(): String {
        val admin = registerAdminAndLogin("audit@example.com")
        val tenant = objectMapper.readValue(
            mockMvc.perform(
                post("/api/admin/platform/tenants")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(CreateTenantRequest(name = "audit-team"))),
            ).andExpect(status().isOk).andReturn().response.contentAsByteArray,
            TenantResponse::class.java,
        ).result
        val project = objectMapper.readValue(
            mockMvc.perform(
                post("/api/platform/tenants/${tenant.id}/projects")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(CreateProjectRequest(name = "audit-project"))),
            ).andExpect(status().isOk).andReturn().response.contentAsByteArray,
            ProjectResponse::class.java,
        ).result
        return objectMapper.readValue(
            mockMvc.perform(
                post("/api/platform/projects/${project.id}/api-keys")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(CreateApiKeyRequest(name = "default"))),
            ).andExpect(status().isOk).andReturn().response.contentAsByteArray,
            ApiKeyIssueResponse::class.java,
        ).result.apiKey
    }

    private fun registerAdminAndLogin(email: String): AuthOutput {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(RegisterRequest(email = email, password = "password123"))),
        ).andExpect(status().isOk)

        val adminUser = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow()
        adminUser.role = UserRole.ADMIN
        userRepository.save(adminUser)

        return objectMapper.readValue(
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(LoginRequest(email = email, password = "password123"))),
            ).andExpect(status().isOk).andReturn().response.contentAsByteArray,
            AuthResponse::class.java,
        ).result
    }
}
