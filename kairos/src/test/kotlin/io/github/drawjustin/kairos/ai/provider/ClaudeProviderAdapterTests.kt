package io.github.drawjustin.kairos.ai.provider

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.drawjustin.kairos.ai.config.AnthropicProperties
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.service.AiToolExecutor
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.github.drawjustin.kairos.context.type.ContextSourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class ClaudeProviderAdapterTests {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `chat completion executes allowed tool call and sends result back to Claude`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val adapter = ClaudeProviderAdapter(
            anthropicProperties = AnthropicProperties(
                baseUrl = "https://api.anthropic.test",
                apiKey = "anthropic-api-key",
            ),
            restClientBuilder = restClientBuilder,
            aiToolExecutor = AiToolExecutor(objectMapper, restClientBuilder),
            objectMapper = objectMapper,
        )
        val tool = AiToolDefinition(
            sourceId = 1,
            name = "hr_policy_search_1",
            description = "인사 정책을 검색할 때 사용한다.",
            sourceType = ContextSourceType.MCP_SERVER,
            sourceUri = "https://mcp.internal/hr/search",
        )
        val request = ChatCompletionRequest(
            model = AiModel.CLAUDE_HAIKU_4_5,
            messages = listOf(
                ChatMessageRequest(ChatRole.SYSTEM, "너는 KAIROS의 사내 AI 어시스턴트다."),
                ChatMessageRequest(ChatRole.USER, "연차 정책 알려줘"),
            ),
        )

        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.tools[0].name").value("hr_policy_search_1"))
            .andExpect(jsonPath("$.tools[0].input_schema.required[0]").value("query"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": "msg_tool_1",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-haiku-4-5-20251001",
                      "content": [
                        {
                          "type": "tool_use",
                          "id": "toolu_1",
                          "name": "hr_policy_search_1",
                          "input": {"query": "연차 정책"}
                        }
                      ],
                      "stop_reason": "tool_use",
                      "usage": {
                        "input_tokens": 20,
                        "output_tokens": 5
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        server.expect(requestTo("https://mcp.internal/hr/search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.query").value("연차 정책"))
            .andRespond(
                withSuccess(
                    """{"documents":["연차는 입사일 기준으로 계산한다."]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server.expect(requestTo("https://api.anthropic.test/v1/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.messages[1].content[0].type").value("tool_use"))
            .andExpect(jsonPath("$.messages[2].content[0].type").value("tool_result"))
            .andExpect(jsonPath("$.messages[2].content[0].tool_use_id").value("toolu_1"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": "msg_final_1",
                      "type": "message",
                      "role": "assistant",
                      "model": "claude-haiku-4-5-20251001",
                      "content": [
                        {
                          "type": "text",
                          "text": "연차는 입사일 기준으로 계산합니다."
                        }
                      ],
                      "stop_reason": "end_turn",
                      "usage": {
                        "input_tokens": 40,
                        "output_tokens": 12
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = adapter.chatCompletion(request, listOf(tool))

        assertThat(response.id).isEqualTo("msg_final_1")
        assertThat(response.choices.single().message.content).isEqualTo("연차는 입사일 기준으로 계산합니다.")
        assertThat(response.usage?.totalTokens).isEqualTo(52)
        server.verify()
    }
}
