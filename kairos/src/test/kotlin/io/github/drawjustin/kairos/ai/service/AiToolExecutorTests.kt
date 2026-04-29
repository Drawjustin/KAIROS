package io.github.drawjustin.kairos.ai.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.context.service.ContextSearchLoggingService
import io.github.drawjustin.kairos.context.type.ContextSearchPurpose
import io.github.drawjustin.kairos.context.type.ContextSourceType
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.tenant.entity.Tenant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
}
