package io.github.drawjustin.kairos.ai.provider

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.ai.config.AnthropicProperties
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.dto.ChatChoiceResponse
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageResponse
import io.github.drawjustin.kairos.ai.dto.ChatUsageResponse
import io.github.drawjustin.kairos.ai.provider.claude.AnthropicContentBlock
import io.github.drawjustin.kairos.ai.provider.claude.AnthropicMessage
import io.github.drawjustin.kairos.ai.provider.claude.AnthropicMessageRequest
import io.github.drawjustin.kairos.ai.provider.claude.AnthropicMessageResponse
import io.github.drawjustin.kairos.ai.provider.claude.AnthropicTool
import io.github.drawjustin.kairos.ai.service.AiToolExecutor
import io.github.drawjustin.kairos.ai.service.AiToolExecutionContext
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.ai.type.AiProvider
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import java.time.Instant
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
// Anthropic Messages API 형식을 KAIROS 공통 chat completion 형식으로 변환한다.
class ClaudeProviderAdapter(
    private val anthropicProperties: AnthropicProperties,
    restClientBuilder: RestClient.Builder,
    private val aiToolExecutor: AiToolExecutor,
    private val objectMapper: ObjectMapper,
) : ProviderAdapter {
    private val restClient = restClientBuilder.build()

    override fun supports(model: AiModel): Boolean = model.provider == AiProvider.CLAUDE

    override fun chatCompletion(
        request: ChatCompletionRequest,
        tools: List<AiToolDefinition>,
        toolExecutionContext: AiToolExecutionContext?,
    ): ChatCompletionResponse {
        val apiKey = anthropicProperties.apiKey.trim()
        if (apiKey.isBlank()) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_NOT_CONFIGURED)
        }

        return try {
            val providerRequest = request.toProviderRequest(tools = tools)
            val response = sendProviderRequest(apiKey = apiKey, request = providerRequest)
            val pendingToolCalls = response.pendingToolCalls()
            if (pendingToolCalls.isEmpty()) {
                response.toChatCompletionResponse()
            } else {
                val followUpMessages = providerRequest.messages +
                    AnthropicMessage(
                        role = ChatRole.ASSISTANT.value,
                        content = pendingToolCalls.map { it.toProviderToolCallBlock() },
                    ) +
                    AnthropicMessage(
                        role = ChatRole.USER.value,
                        content = pendingToolCalls.map { toolCall ->
                            mapOf(
                                "type" to "tool_result",
                                "tool_use_id" to toolCall.id,
                                "content" to toolCall.executeTool(tools, toolExecutionContext),
                            )
                        },
                    )
                sendProviderRequest(
                    apiKey = apiKey,
                    request = providerRequest.copy(messages = followUpMessages),
                ).toChatCompletionResponse()
            }
        } catch (exception: KairosException) {
            throw exception
        } catch (exception: Exception) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR, exception.message ?: KairosErrorCode.AI_PROVIDER_ERROR.message)
        }
    }

    private fun sendProviderRequest(apiKey: String, request: AnthropicMessageRequest): AnthropicMessageResponse =
        restClient.post()
            .uri("${anthropicProperties.baseUrl.trimEnd('/')}/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", anthropicProperties.version)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body<AnthropicMessageResponse>()
            ?: throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR)

    private fun ChatCompletionRequest.toProviderRequest(tools: List<AiToolDefinition>): AnthropicMessageRequest {
        val systemPrompt = messages
            .filter { it.role == ChatRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .ifBlank { null }
        val conversationMessages = messages
            .filterNot { it.role == ChatRole.SYSTEM }
            .map {
                AnthropicMessage(
                    role = it.role.value,
                    content = it.content,
                )
            }

        return AnthropicMessageRequest(
            model = model.value,
            maxTokens = maxTokens ?: DEFAULT_MAX_TOKENS,
            messages = conversationMessages,
            system = systemPrompt,
            temperature = temperature,
            tools = tools.toProviderTools().takeIf { it.isNotEmpty() },
        )
    }

    private fun List<AiToolDefinition>.toProviderTools(): List<AnthropicTool> =
        map {
            AnthropicTool(
                name = it.name,
                description = it.description,
                inputSchema = it.parameters,
            )
        }

    private fun AnthropicMessageResponse.pendingToolCalls() =
        content.filter { it.type == "tool_use" }

    private fun AnthropicContentBlock.toProviderToolCallBlock(): Map<String, Any> =
        mapOf(
            "type" to "tool_use",
            "id" to requireNotNull(id) { "Claude tool_use id must exist" },
            "name" to requireNotNull(name) { "Claude tool_use name must exist" },
            "input" to input.orEmpty(),
        )

    private fun AnthropicContentBlock.executeTool(
        tools: List<AiToolDefinition>,
        context: AiToolExecutionContext?,
    ): String {
        val tool = tools.firstOrNull { it.name == name }
            ?: throw KairosException(KairosErrorCode.AI_TOOL_NOT_ALLOWED)
        return aiToolExecutor.execute(
            tool = tool,
            arguments = objectMapper.writeValueAsString(input.orEmpty()),
            context = context,
        )
    }

    private fun AnthropicMessageResponse.toChatCompletionResponse(): ChatCompletionResponse {
        val text = content
            .filter { it.type == "text" }
            .joinToString("") { it.text.orEmpty() }

        return ChatCompletionResponse(
            id = id,
            `object` = "chat.completion",
            created = Instant.now().epochSecond,
            model = model,
            choices = listOf(
                ChatChoiceResponse(
                    index = 0,
                    message = ChatMessageResponse(
                        role = ChatRole.ASSISTANT,
                        content = text,
                    ),
                    finishReason = stopReason,
                ),
            ),
            usage = ChatUsageResponse(
                promptTokens = usage.inputTokens,
                completionTokens = usage.outputTokens,
                totalTokens = usage.inputTokens + usage.outputTokens,
            ),
        )
    }

    private companion object {
        private const val DEFAULT_MAX_TOKENS = 1024
    }
}
