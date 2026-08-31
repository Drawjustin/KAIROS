package io.github.drawjustin.kairos.ai

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.ai.repository.AiUsageLogRepository
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.type.AiUsageStatus
import io.github.drawjustin.kairos.ai.dto.ChatChoiceResponse
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.dto.ChatMessageResponse
import io.github.drawjustin.kairos.ai.dto.ChatUsageResponse
import io.github.drawjustin.kairos.ai.provider.ProviderAdapter
import io.github.drawjustin.kairos.ai.provider.ProviderRouter
import io.github.drawjustin.kairos.ai.service.AiToolExecutionContext
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.github.drawjustin.kairos.auth.dto.AuthOutput
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.budget.entity.ProjectBudget
import io.github.drawjustin.kairos.budget.repository.ProjectBudgetRepository
import io.github.drawjustin.kairos.budget.type.BudgetPeriod
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.pii.repository.PiiDetectionLogRepository
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiType
import io.github.drawjustin.kairos.platform.dto.ApiKeyIssueResponse
import io.github.drawjustin.kairos.platform.dto.CreateApiKeyRequest
import io.github.drawjustin.kairos.platform.dto.CreateProjectRequest
import io.github.drawjustin.kairos.platform.dto.CreateTenantRequest
import io.github.drawjustin.kairos.platform.dto.ProjectResponse
import io.github.drawjustin.kairos.platform.dto.TenantResponse
import io.github.drawjustin.kairos.project.entity.ProjectAllowedModel
import io.github.drawjustin.kairos.project.repository.ProjectAllowedModelRepository
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.repository.TenantRepository
import io.github.drawjustin.kairos.tenant.repository.TenantUserRepository
import io.github.drawjustin.kairos.user.repository.UserRepository
import io.github.drawjustin.kairos.user.type.UserRole
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
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
    lateinit var projectAllowedModelRepository: ProjectAllowedModelRepository

    @Autowired
    lateinit var aiUsageLogRepository: AiUsageLogRepository

    @Autowired
    lateinit var piiDetectionLogRepository: PiiDetectionLogRepository

    @Autowired
    lateinit var projectBudgetRepository: ProjectBudgetRepository

    @MockitoBean
    lateinit var providerRouter: ProviderRouter

    lateinit var providerAdapter: ProviderAdapter

    @BeforeEach
    fun setUp() {
        // soft delete된 row까지 포함해 초기 상태를 맞춰 API key 인증 테스트가 흔들리지 않게 한다.
        jdbcTemplate.execute(
            "truncate table pii_detection_log, project_pii_policy, project_budget, context_search_log, ai_usage_log, api_key, project_context_source, context_source, project_allowed_model, project, tenant_user, tenant, refresh_session, users restart identity cascade",
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
                    role = ChatRole.USER,
                    content = "안녕",
                ),
            ),
        )

        val enrichedRequest = request.copy(
            messages = listOf(
                ChatMessageRequest(
                    role = ChatRole.SYSTEM,
                    content = "너는 KAIROS의 사내 AI 어시스턴트다. 내부 문서를 우선 참고하고, 근거 없는 내용은 추측하지 마라.",
                ),
            ) + request.messages,
        )

        given(providerRouter.route(AiModel.GPT_4O_MINI)).willReturn(providerAdapter)
        given(
            providerAdapter.chatCompletion(
                eqNotNull(enrichedRequest),
                eqNotNull(emptyList<AiToolDefinition>()),
                any(AiToolExecutionContext::class.java),
            ),
        )
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
                                role = ChatRole.ASSISTANT,
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
                                    role = ChatRole.USER,
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
    fun `chat completions rejects model that is not allowed for project`() {
        val adminLogin = registerAdminAndLogin("ai-model-policy-admin@example.com")
        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "ai-model-policy-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "ai-model-policy-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default"))
        val projectEntity = projectRepository.findByIdAndDeletedAtIsNull(project.id).orElseThrow()

        val currentPolicies = projectAllowedModelRepository.findAllByProject_IdAndDeletedAtIsNullOrderByModelAsc(project.id)
        projectAllowedModelRepository.deleteAll(currentPolicies)
        projectAllowedModelRepository.flush()
        projectAllowedModelRepository.save(
            ProjectAllowedModel(
                project = projectEntity,
                model = AiModel.GEMINI_2_5_FLASH,
            ),
        )

        mockMvc.perform(
            post("/api/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${issuedKey.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        ChatCompletionRequest(
                            model = AiModel.GPT_4O_MINI,
                            messages = listOf(
                                ChatMessageRequest(
                                    role = ChatRole.USER,
                                    content = "안녕",
                                ),
                            ),
                        ),
                    ),
                ),
        )
            .andExpect(status().isForbidden)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo("AI_008")
            }

        val usageLog = aiUsageLogRepository.findAll().single()
        assertThat(usageLog.project.id).isEqualTo(project.id)
        assertThat(usageLog.status).isEqualTo(AiUsageStatus.FAILED)
        assertThat(usageLog.errorCode).isEqualTo("AI_008")
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
                    role = ChatRole.USER,
                    content = "안녕",
                ),
            ),
        )
        val enrichedRequest = request.copy(
            messages = listOf(
                ChatMessageRequest(
                    role = ChatRole.SYSTEM,
                    content = "너는 KAIROS의 사내 AI 어시스턴트다. 내부 문서를 우선 참고하고, 근거 없는 내용은 추측하지 마라.",
                ),
            ) + request.messages,
        )

        given(providerRouter.route(AiModel.GPT_4O_MINI)).willReturn(providerAdapter)
        given(
            providerAdapter.chatCompletion(
                eqNotNull(enrichedRequest),
                eqNotNull(emptyList<AiToolDefinition>()),
                any(AiToolExecutionContext::class.java),
            ),
        )
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

    @Test
    fun `chat completions blocks a prompt that carries a resident registration number`() {
        val adminLogin = registerAdminAndLogin("pii-block@example.com")
        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "pii-block-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "pii-block-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default"))
        val request = ChatCompletionRequest(
            model = AiModel.GPT_4O_MINI,
            messages = listOf(
                ChatMessageRequest(
                    role = ChatRole.USER,
                    content = "고객 주민번호 900101-1234567 조회해줘",
                ),
            ),
        )

        val result = mockMvc.perform(
            post("/api/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${issuedKey.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isForbidden)
            .andReturn()

        val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
        assertThat(response.errorCode).isEqualTo("AI_011")
        // 차단 응답이 오히려 값을 흘리면 통제의 의미가 없다.
        assertThat(result.response.contentAsString).doesNotContain("900101")
        assertThat(result.response.contentAsString).doesNotContain("1234567")

        // provider까지 가지 않는다.
        verify(providerRouter, never()).route(AiModel.GPT_4O_MINI)

        val usageLog = aiUsageLogRepository.findAll().single()
        assertThat(usageLog.status).isEqualTo(AiUsageStatus.FAILED)
        assertThat(usageLog.errorCode).isEqualTo("AI_011")

        val detection = piiDetectionLogRepository.findAllByProject_IdOrderByIdAsc(project.id).single()
        assertThat(detection.piiType).isEqualTo(PiiType.RESIDENT_REGISTRATION_NUMBER)
        assertThat(detection.action).isEqualTo(PiiAction.BLOCK)
        assertThat(detection.traceId).isNotBlank()
    }

    @Test
    fun `chat completions masks a phone number before the prompt reaches the provider`() {
        val adminLogin = registerAdminAndLogin("pii-mask@example.com")
        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "pii-mask-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "pii-mask-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default"))
        val request = ChatCompletionRequest(
            model = AiModel.GPT_4O_MINI,
            messages = listOf(
                ChatMessageRequest(
                    role = ChatRole.USER,
                    content = "담당자 연락처 010-1234-5678 로 안내문 초안 써줘",
                ),
            ),
        )

        // provider가 실제로 받는 요청. 스텁이 이 값과 일치하지 않으면 테스트가 실패한다.
        val expectedRequest = request.copy(
            messages = listOf(
                ChatMessageRequest(
                    role = ChatRole.SYSTEM,
                    content = "너는 KAIROS의 사내 AI 어시스턴트다. 내부 문서를 우선 참고하고, 근거 없는 내용은 추측하지 마라.",
                ),
                ChatMessageRequest(
                    role = ChatRole.USER,
                    content = "담당자 연락처 010-****-5678 로 안내문 초안 써줘",
                ),
            ),
        )

        given(providerRouter.route(AiModel.GPT_4O_MINI)).willReturn(providerAdapter)
        given(
            providerAdapter.chatCompletion(
                eqNotNull(expectedRequest),
                eqNotNull(emptyList<AiToolDefinition>()),
                any(AiToolExecutionContext::class.java),
            ),
        )
            .willReturn(
                ChatCompletionResponse(
                    id = "chatcmpl_masked",
                    `object` = "chat.completion",
                    created = 1_713_086_400,
                    model = "gpt-4o-mini",
                    choices = listOf(
                        ChatChoiceResponse(
                            index = 0,
                            message = ChatMessageResponse(role = ChatRole.ASSISTANT, content = "초안입니다"),
                            finishReason = "stop",
                        ),
                    ),
                    usage = ChatUsageResponse(promptTokens = 10, completionTokens = 5, totalTokens = 15),
                ),
            )

        mockMvc.perform(
            post("/api/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${issuedKey.apiKey}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isOk)

        val detection = piiDetectionLogRepository.findAllByProject_IdOrderByIdAsc(project.id).single()
        assertThat(detection.piiType).isEqualTo(PiiType.PHONE_NUMBER)
        assertThat(detection.action).isEqualTo(PiiAction.MASK)
    }

    @Test
    fun `chat completions refuses the call once the request limit is used up`() {
        val adminLogin = registerAdminAndLogin("budget-limit@example.com")
        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "budget-limit-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "budget-limit-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default"))
        givenBudget(project.id, requestLimit = 1)
        givenProviderReturns("첫 번째 질문", totalTokens = 22)

        performChat(issuedKey.apiKey, "첫 번째 질문").andExpect(status().isOk)

        val result = performChat(issuedKey.apiKey, "첫 번째 질문")
            .andExpect(status().isTooManyRequests)
            .andReturn()
        assertThat(objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java).errorCode)
            .isEqualTo("AI_012")

        val budget = reloadBudget(project.id)
        assertThat(budget.consumedRequests).isEqualTo(1)
        assertThat(aiUsageLogRepository.findAll().count { it.status == AiUsageStatus.FAILED }).isEqualTo(1)
    }

    @Test
    fun `chat completions settles the tokens the response actually used`() {
        val adminLogin = registerAdminAndLogin("budget-settle@example.com")
        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "budget-settle-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "budget-settle-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default"))
        givenBudget(project.id, requestLimit = 10, tokenLimit = 1_000)
        givenProviderReturns("첫 번째 질문", totalTokens = 22)

        performChat(issuedKey.apiKey, "첫 번째 질문").andExpect(status().isOk)

        val budget = reloadBudget(project.id)
        assertThat(budget.consumedRequests).isEqualTo(1)
        assertThat(budget.consumedTokens).isEqualTo(22)
    }

    @Test
    fun `chat completions gives the reservation back when the provider fails`() {
        val adminLogin = registerAdminAndLogin("budget-release@example.com")
        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "budget-release-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "budget-release-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default"))
        givenBudget(project.id, requestLimit = 1)

        given(providerRouter.route(AiModel.GPT_4O_MINI)).willReturn(providerAdapter)
        given(
            providerAdapter.chatCompletion(
                eqNotNull(enrichedRequestOf("첫 번째 질문")),
                eqNotNull(emptyList<AiToolDefinition>()),
                any(AiToolExecutionContext::class.java),
            ),
        )
            .willThrow(KairosException(KairosErrorCode.AI_PROVIDER_ERROR))

        performChat(issuedKey.apiKey, "첫 번째 질문").andExpect(status().isInternalServerError)

        // 호출이 실패했으니 선점분은 되돌아가고 한도는 그대로 남아 있어야 한다.
        assertThat(reloadBudget(project.id).consumedRequests).isEqualTo(0)
    }

    @Test
    fun `a prompt blocked for sensitive data does not consume the budget`() {
        val adminLogin = registerAdminAndLogin("budget-pii@example.com")
        val tenant = createTenant(adminLogin.accessToken, CreateTenantRequest(name = "budget-pii-team"))
        val project = createProject(adminLogin.accessToken, tenant.id, CreateProjectRequest(name = "budget-pii-project"))
        val issuedKey = createApiKey(adminLogin.accessToken, project.id, CreateApiKeyRequest(name = "default"))
        givenBudget(project.id, requestLimit = 1)

        performChat(issuedKey.apiKey, "주민번호 900101-1234567 조회해줘").andExpect(status().isForbidden)

        // 차단된 요청이 한도를 갉아먹으면 정상 요청이 밀려난다.
        assertThat(reloadBudget(project.id).consumedRequests).isEqualTo(0)
    }

    private fun givenBudget(projectId: Long, requestLimit: Long? = null, tokenLimit: Long? = null) {
        projectBudgetRepository.saveAndFlush(
            ProjectBudget(
                project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(),
                period = BudgetPeriod.DAILY,
                requestLimit = requestLimit,
                tokenLimit = tokenLimit,
                periodStartedAt = Instant.now(),
            ),
        )
    }

    private fun reloadBudget(projectId: Long) =
        projectBudgetRepository.findByProject_IdAndPeriodAndDeletedAtIsNull(projectId, BudgetPeriod.DAILY)!!

    // 민감정보가 없는 프롬프트라 KAIROS가 붙이는 시스템 메시지만 앞에 추가된다.
    private fun enrichedRequestOf(content: String) = ChatCompletionRequest(
        model = AiModel.GPT_4O_MINI,
        messages = listOf(
            ChatMessageRequest(
                role = ChatRole.SYSTEM,
                content = "너는 KAIROS의 사내 AI 어시스턴트다. 내부 문서를 우선 참고하고, 근거 없는 내용은 추측하지 마라.",
            ),
            ChatMessageRequest(role = ChatRole.USER, content = content),
        ),
    )

    private fun givenProviderReturns(content: String, totalTokens: Int) {
        given(providerRouter.route(AiModel.GPT_4O_MINI)).willReturn(providerAdapter)
        given(
            providerAdapter.chatCompletion(
                eqNotNull(enrichedRequestOf(content)),
                eqNotNull(emptyList<AiToolDefinition>()),
                any(AiToolExecutionContext::class.java),
            ),
        )
            .willReturn(
                ChatCompletionResponse(
                    id = "chatcmpl_budget",
                    `object` = "chat.completion",
                    created = 1_713_086_400,
                    model = "gpt-4o-mini",
                    choices = listOf(
                        ChatChoiceResponse(
                            index = 0,
                            message = ChatMessageResponse(role = ChatRole.ASSISTANT, content = "네"),
                            finishReason = "stop",
                        ),
                    ),
                    usage = ChatUsageResponse(
                        promptTokens = totalTokens / 2,
                        completionTokens = totalTokens - totalTokens / 2,
                        totalTokens = totalTokens,
                    ),
                ),
            )
    }

    private fun performChat(apiKey: String, content: String) =
        mockMvc.perform(
            post("/api/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        ChatCompletionRequest(
                            model = AiModel.GPT_4O_MINI,
                            messages = listOf(ChatMessageRequest(role = ChatRole.USER, content = content)),
                        ),
                    ),
                ),
        )

    private fun <T> eqNotNull(value: T): T = eq(value) ?: value

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
