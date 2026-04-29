package io.github.drawjustin.kairos.ai.provider

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.drawjustin.kairos.ai.config.OpenAiProperties
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.service.AiToolExecutor
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.github.drawjustin.kairos.context.service.ContextSearchLoggingService
import io.github.drawjustin.kairos.context.type.ContextSourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OpenAiProviderAdapterTests {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `chat completion executes allowed tool call and sends result back to OpenAI`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val adapter = OpenAiProviderAdapter(
            openAiProperties = OpenAiProperties(
                baseUrl = "https://api.openai.test",
                apiKey = "openai-api-key",
            ),
            restClientBuilder = restClientBuilder,
            aiToolExecutor = AiToolExecutor(
                objectMapper = objectMapper,
                contextSearchLoggingService = mock(ContextSearchLoggingService::class.java),
                restClientBuilder = restClientBuilder,
            ),
        )
        val tool = AiToolDefinition(
            sourceId = 1,
            name = "hr_policy_search_1",
            description = "인사 정책을 검색할 때 사용한다.",
            sourceType = ContextSourceType.MCP_SERVER,
            sourceUri = "https://mcp.internal/hr/search",
        )
        val request = ChatCompletionRequest(
            model = AiModel.GPT_4O_MINI,
            messages = listOf(
                ChatMessageRequest(
                    role = ChatRole.SYSTEM,
                    content = "너는 KAIROS의 사내 AI 어시스턴트다.",
                ),
                ChatMessageRequest(
                    role = ChatRole.USER,
                    content = "연차 정책 알려줘",
                ),
            ),
        )

        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.tools[0].function.name").value("hr_policy_search_1"))
            .andExpect(jsonPath("$.tools[0].function.parameters.required[0]").value("query"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": "chatcmpl_tool_1",
                      "object": "chat.completion",
                      "created": 1713086400,
                      "model": "gpt-4o-mini",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [
                              {
                                "id": "call_1",
                                "type": "function",
                                "function": {
                                  "name": "hr_policy_search_1",
                                  "arguments": "{\"query\":\"연차 정책\"}"
                                }
                              }
                            ]
                          },
                          "finish_reason": "tool_calls"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 20,
                        "completion_tokens": 5,
                        "total_tokens": 25
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
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.messages[2].tool_calls[0].function.name").value("hr_policy_search_1"))
            .andExpect(jsonPath("$.messages[3].role").value("tool"))
            .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": "chatcmpl_final_1",
                      "object": "chat.completion",
                      "created": 1713086401,
                      "model": "gpt-4o-mini",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "연차는 입사일 기준으로 계산합니다."
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 40,
                        "completion_tokens": 12,
                        "total_tokens": 52
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = adapter.chatCompletion(request, listOf(tool))

        assertThat(response.id).isEqualTo("chatcmpl_final_1")
        assertThat(response.choices.single().message.content).isEqualTo("연차는 입사일 기준으로 계산합니다.")
        assertThat(response.usage?.totalTokens).isEqualTo(52)
        server.verify()
    }
}
