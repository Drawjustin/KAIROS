package io.github.drawjustin.kairos.resilience

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
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.type.AiUsageStatus
import io.github.drawjustin.kairos.ai.type.ChatRole
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
import io.github.drawjustin.kairos.project.entity.ProjectAllowedModel
import io.github.drawjustin.kairos.project.repository.ProjectAllowedModelRepository
import io.github.drawjustin.kairos.project.repository.ProjectRepository
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
// provider 하나가 죽었을 때 project가 이미 허용한 다른 provider로 서비스가 이어지는지,
// 그리고 그 우회가 권한 통제를 넘지 않는지 확인한다.
class ProviderFallbackTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var projectRepository: ProjectRepository

    @Autowired
    lateinit var projectAllowedModelRepository: ProjectAllowedModelRepository

    @Autowired
    lateinit var aiUsageLogRepository: AiUsageLogRepository

    @MockitoBean
    lateinit var providerRouter: ProviderRouter

    private lateinit var openAiAdapter: ProviderAdapter
    private lateinit var claudeAdapter: ProviderAdapter

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "truncate table pii_detection_log, project_pii_policy, project_budget, context_search_log, " +
                "ai_usage_log, api_key, project_context_source, context_source, project_allowed_model, " +
                "project, tenant_user, tenant, refresh_session, users restart identity cascade",
        )
        openAiAdapter = mock(ProviderAdapter::class.java)
        claudeAdapter = mock(ProviderAdapter::class.java)
        given(providerRouter.route(AiModel.GPT_4O_MINI)).willReturn(openAiAdapter)
        given(providerRouter.route(AiModel.CLAUDE_HAIKU_4_5)).willReturn(claudeAdapter)
    }

    @Test
    fun `falls back to another provider that the project already allows`() {
        val setUpProject = givenProject("fallback-ok@example.com", "fallback-ok")
        allowOnly(setUpProject.projectId, AiModel.GPT_4O_MINI, AiModel.CLAUDE_HAIKU_4_5)
        givenOpenAiIsDown()
        givenClaudeAnswers()

        val result = performChat(setUpProject.apiKey).andExpect(status().isOk).andReturn()

        val response = objectMapper.readValue(result.response.contentAsByteArray, ChatCompletionResponse::class.java)
        assertThat(response.id).isEqualTo("chatcmpl_fallback")

        val usageLog = aiUsageLogRepository.findAll().single { it.status == AiUsageStatus.SUCCESS }
        assertThat(usageLog.provider).isEqualTo("CLAUDE")
        assertThat(usageLog.isFallback).isTrue()
        // 요청한 모델과 실제로 답한 모델이 모두 남아야 사후에 대체가 얼마나 일어났는지 셀 수 있다.
        assertThat(usageLog.fallbackFromModel).isEqualTo(AiModel.GPT_4O_MINI.value)
        assertThat(usageLog.model).isEqualTo(AiModel.CLAUDE_HAIKU_4_5.value)
    }

    @Test
    fun `refuses to fall back to a model the project has not allowed`() {
        val setUpProject = givenProject("fallback-policy@example.com", "fallback-policy")
        // Claude는 붙어 있지만 이 project에는 허용되지 않았다.
        allowOnly(setUpProject.projectId, AiModel.GPT_4O_MINI)
        givenOpenAiIsDown()
        givenClaudeAnswers()

        val result = performChat(setUpProject.apiKey).andExpect(status().isServiceUnavailable).andReturn()

        assertThat(objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java).errorCode)
            .isEqualTo("AI_014")
        // 장애 대응이 권한 통제를 넘어서면 안 된다. 가용성보다 통제가 우선이다.
        assertThat(aiUsageLogRepository.findAll().none { it.isFallback }).isTrue()
    }

    @Test
    fun `does not fall back when the request itself was rejected`() {
        val setUpProject = givenProject("fallback-4xx@example.com", "fallback-4xx")
        allowOnly(setUpProject.projectId, AiModel.GPT_4O_MINI, AiModel.CLAUDE_HAIKU_4_5)
        given(
            openAiAdapter.chatCompletion(
                any(ChatCompletionRequest::class.java) ?: sampleRequest(),
                anyToolList(),
                any(AiToolExecutionContext::class.java),
            ),
        )
            .willThrow(KairosException(KairosErrorCode.AI_PROVIDER_ERROR))
        givenClaudeAnswers()

        performChat(setUpProject.apiKey).andExpect(status().isInternalServerError)

        // 잘못된 요청을 다른 provider에 넘겨봐야 거기서도 똑같이 거절당한다.
        assertThat(aiUsageLogRepository.findAll().none { it.isFallback }).isTrue()
    }

    private fun anyToolList(): List<AiToolDefinition> =
        any(List::class.java) as? List<AiToolDefinition> ?: emptyList()

    private fun givenOpenAiIsDown() {
        given(
            openAiAdapter.chatCompletion(
                any(ChatCompletionRequest::class.java) ?: sampleRequest(),
                anyToolList(),
                any(AiToolExecutionContext::class.java),
            ),
        )
            .willThrow(KairosException(KairosErrorCode.AI_PROVIDER_UNAVAILABLE))
    }

    private fun givenClaudeAnswers() {
        given(
            claudeAdapter.chatCompletion(
                any(ChatCompletionRequest::class.java) ?: sampleRequest(),
                anyToolList(),
                any(AiToolExecutionContext::class.java),
            ),
        )
            .willReturn(
                ChatCompletionResponse(
                    id = "chatcmpl_fallback",
                    `object` = "chat.completion",
                    created = 1_713_086_400,
                    model = AiModel.CLAUDE_HAIKU_4_5.value,
                    choices = listOf(
                        ChatChoiceResponse(
                            index = 0,
                            message = ChatMessageResponse(role = ChatRole.ASSISTANT, content = "대체 응답"),
                            finishReason = "stop",
                        ),
                    ),
                    usage = ChatUsageResponse(promptTokens = 5, completionTokens = 5, totalTokens = 10),
                ),
            )
    }

    private fun sampleRequest() = ChatCompletionRequest(
        model = AiModel.GPT_4O_MINI,
        messages = listOf(ChatMessageRequest(role = ChatRole.USER, content = "안녕")),
    )

    private fun allowOnly(projectId: Long, vararg models: AiModel) {
        val project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow()
        projectAllowedModelRepository.findAllByProject_IdAndDeletedAtIsNullOrderByModelAsc(projectId)
            .forEach { projectAllowedModelRepository.delete(it) }
        projectAllowedModelRepository.flush()
        models.forEach {
            projectAllowedModelRepository.saveAndFlush(ProjectAllowedModel(project = project, model = it))
        }
    }

    private data class ProjectSetUp(val projectId: Long, val apiKey: String)

    private fun givenProject(email: String, name: String): ProjectSetUp {
        val admin = registerAdminAndLogin(email)
        val tenant = objectMapper.readValue(
            mockMvc.perform(
                post("/api/admin/platform/tenants")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(CreateTenantRequest(name = "$name-team"))),
            ).andExpect(status().isOk).andReturn().response.contentAsByteArray,
            TenantResponse::class.java,
        ).result
        val project = objectMapper.readValue(
            mockMvc.perform(
                post("/api/platform/tenants/${tenant.id}/projects")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(CreateProjectRequest(name = "$name-project"))),
            ).andExpect(status().isOk).andReturn().response.contentAsByteArray,
            ProjectResponse::class.java,
        ).result
        val issued = objectMapper.readValue(
            mockMvc.perform(
                post("/api/platform/projects/${project.id}/api-keys")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin.accessToken}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(CreateApiKeyRequest(name = "default"))),
            ).andExpect(status().isOk).andReturn().response.contentAsByteArray,
            ApiKeyIssueResponse::class.java,
        ).result
        return ProjectSetUp(project.id, issued.apiKey)
    }

    private fun registerAdminAndLogin(email: String): AuthOutput {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(RegisterRequest(email = email, password = "password123")),
                ),
        ).andExpect(status().isOk)

        val adminUser = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow()
        adminUser.role = UserRole.ADMIN
        userRepository.save(adminUser)

        return objectMapper.readValue(
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(LoginRequest(email = email, password = "password123")),
                    ),
            ).andExpect(status().isOk).andReturn().response.contentAsByteArray,
            AuthResponse::class.java,
        ).result
    }

    private fun performChat(apiKey: String) =
        mockMvc.perform(
            post("/api/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(sampleRequest())),
        )
}
