package io.github.drawjustin.kairos.ai.provider

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.drawjustin.kairos.ai.config.GeminiProperties
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

class GeminiProviderAdapterTests {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `chat completion executes allowed function call and sends result back to Gemini`() {
        val restClientBuilder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val adapter = GeminiProviderAdapter(
            geminiProperties = GeminiProperties(
                baseUrl = "https://generativelanguage.test",
                apiKey = "gemini-api-key",
            ),
            restClientBuilder = restClientBuilder,
            aiToolExecutor = AiToolExecutor(
                objectMapper = objectMapper,
                contextSearchLoggingService = mock(ContextSearchLoggingService::class.java),
                restClientBuilder = restClientBuilder,
            ),
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
            model = AiModel.GEMINI_2_5_FLASH,
            messages = listOf(
                ChatMessageRequest(ChatRole.SYSTEM, "너는 KAIROS의 사내 AI 어시스턴트다."),
                ChatMessageRequest(ChatRole.USER, "연차 정책 알려줘"),
            ),
        )

        server.expect(requestTo("https://generativelanguage.test/v1beta/models/gemini-2.5-flash:generateContent"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.tools[0].functionDeclarations[0].name").value("hr_policy_search_1"))
            .andExpect(jsonPath("$.tools[0].functionDeclarations[0].parameters.required[0]").value("query"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "responseId": "gemini_tool_1",
                      "modelVersion": "gemini-2.5-flash",
                      "candidates": [
                        {
                          "index": 0,
                          "content": {
                            "role": "model",
                            "parts": [
                              {
                                "functionCall": {
                                  "name": "hr_policy_search_1",
                                  "args": {"query": "연차 정책"}
                                }
                              }
                            ]
                          },
                          "finishReason": "STOP"
                        }
                      ],
                      "usageMetadata": {
                        "promptTokenCount": 20,
                        "candidatesTokenCount": 5,
                        "totalTokenCount": 25
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
        server.expect(requestTo("https://generativelanguage.test/v1beta/models/gemini-2.5-flash:generateContent"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.contents[1].parts[0].functionCall.name").value("hr_policy_search_1"))
            .andExpect(jsonPath("$.contents[2].parts[0].functionResponse.name").value("hr_policy_search_1"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "responseId": "gemini_final_1",
                      "modelVersion": "gemini-2.5-flash",
                      "candidates": [
                        {
                          "index": 0,
                          "content": {
                            "role": "model",
                            "parts": [
                              {
                                "text": "연차는 입사일 기준으로 계산합니다."
                              }
                            ]
                          },
                          "finishReason": "STOP"
                        }
                      ],
                      "usageMetadata": {
                        "promptTokenCount": 40,
                        "candidatesTokenCount": 12,
                        "totalTokenCount": 52
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = adapter.chatCompletion(request, listOf(tool))

        assertThat(response.id).isEqualTo("gemini_final_1")
        assertThat(response.choices.single().message.content).isEqualTo("연차는 입사일 기준으로 계산합니다.")
        assertThat(response.usage?.totalTokens).isEqualTo(52)
        server.verify()
    }
}
