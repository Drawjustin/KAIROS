package io.github.drawjustin.kairos.ai.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.pii.defaultPolicyPiiGuard
import io.github.drawjustin.kairos.pii.passThroughPiiGuard
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.context.service.ContextSearchLoggingService
import io.github.drawjustin.kairos.context.type.ContextSearchPurpose
import io.github.drawjustin.kairos.context.type.ContextSourceType
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.tenant.entity.Tenant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class AiToolExecutorTests {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `tool execution records context search audit log when execution context exists`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val contextSearchLoggingService = mock(ContextSearchLoggingService::class.java)
        val executor = AiToolExecutor(
            objectMapper = objectMapper,
            contextSearchLoggingService = contextSearchLoggingService,
            piiGuard = passThroughPiiGuard(),
            restClientBuilder = restClientBuilder,
        )
        val project = Project(
            id = 10,
            tenant = Tenant(id = 1, name = "platform"),
            name = "kairos",
        )
        val tool = AiToolDefinition(
            sourceId = 3,
            name = "hr_policy_search_3",
            description = "인사 정책을 검색한다.",
            sourceType = ContextSourceType.MCP_SERVER,
            sourceUri = "https://mcp.internal/hr/search",
        )

        server.expect(requestTo("https://mcp.internal/hr/search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.query").value("연차 정책"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "documents": [
                        {"title": "연차 정책", "content": "연차는 입사일 기준으로 계산한다."},
                        {"title": "휴가 신청", "content": "휴가는 3영업일 전에 신청한다."}
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        executor.execute(
            tool = tool,
            arguments = """{"query":"연차 정책"}""",
            context = AiToolExecutionContext(
                userId = 2,
                project = project,
                purpose = ContextSearchPurpose.INTERNAL_QA,
            ),
        )

        val invocation = mockingDetails(contextSearchLoggingService).invocations
            .single { it.method.name == "recordSuccess" && it.arguments.size == 8 }
        assertThat(invocation.arguments[0]).isEqualTo(2L)
        assertThat(invocation.arguments[1]).isEqualTo(project)
        assertThat(invocation.arguments[2]).isEqualTo(ContextSearchPurpose.INTERNAL_QA)
        assertThat(invocation.arguments[3]).isEqualTo("연차 정책")
        assertThat(invocation.arguments[4]).isEqualTo(listOf(3L))
        assertThat(invocation.arguments[5]).isEqualTo(listOf(3L))
        assertThat(invocation.arguments[6]).isEqualTo(2)
        assertThat(invocation.arguments[7] as Long).isGreaterThanOrEqualTo(0)
        server.verify()
    }

    @Test
    fun `masks sensitive values inside a tool result before handing it back to the provider`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val executor = AiToolExecutor(
            objectMapper = objectMapper,
            contextSearchLoggingService = mock(ContextSearchLoggingService::class.java),
            piiGuard = defaultPolicyPiiGuard(),
            restClientBuilder = restClientBuilder,
        )
        val tool = crmTool()

        server.expect(requestTo("https://mcp.internal/crm/search"))
            .andRespond(
                withSuccess(
                    """
                    {"documents": [{"title": "고객 카드", "content": "담당자 010-1234-5678 / hong@example.com"}]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = executor.executeQuery(tool = tool, query = "고객 연락처", project = project())

        assertThat(result).contains("010-****-5678")
        assertThat(result).contains("h***@example.com")
        assertThat(result).doesNotContain("010-1234-5678")
        assertThat(result).doesNotContain("hong@example.com")
        server.verify()
    }

    @Test
    fun `blocks a tool result that carries a resident registration number`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val executor = AiToolExecutor(
            objectMapper = objectMapper,
            contextSearchLoggingService = mock(ContextSearchLoggingService::class.java),
            piiGuard = defaultPolicyPiiGuard(),
            restClientBuilder = restClientBuilder,
        )
        val tool = crmTool()

        server.expect(requestTo("https://mcp.internal/crm/search"))
            .andRespond(
                withSuccess(
                    """{"documents": [{"title": "가입 원본", "content": "주민번호 900101-1234567"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val exception = assertThrows<KairosException> {
            executor.executeQuery(tool = tool, query = "가입 정보", project = project())
        }

        assertThat(exception.errorCode).isEqualTo(KairosErrorCode.AI_SENSITIVE_DATA_BLOCKED)
        server.verify()
    }

    @Test
    fun `leaves the tool result untouched when no project is given`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val executor = AiToolExecutor(
            objectMapper = objectMapper,
            contextSearchLoggingService = mock(ContextSearchLoggingService::class.java),
            piiGuard = defaultPolicyPiiGuard(),
            restClientBuilder = restClientBuilder,
        )
        val tool = crmTool()

        server.expect(requestTo("https://mcp.internal/crm/search"))
            .andRespond(
                withSuccess("""{"documents": [{"content": "담당자 010-1234-5678"}]}""", MediaType.APPLICATION_JSON),
            )

        val result = executor.executeQuery(tool = tool, query = "고객 연락처")

        assertThat(result).contains("010-1234-5678")
        server.verify()
    }

    private fun project() = Project(id = 10, tenant = Tenant(id = 1, name = "platform"), name = "kairos")

    private fun crmTool() = AiToolDefinition(
        sourceId = 4,
        name = "crm_search",
        description = "고객 정보를 검색한다.",
        sourceType = ContextSourceType.MCP_SERVER,
        sourceUri = "https://mcp.internal/crm/search",
    )
}
