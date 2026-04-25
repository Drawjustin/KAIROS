package io.github.drawjustin.kairos.ai.provider

import io.github.drawjustin.kairos.ai.config.AnthropicProperties
import io.github.drawjustin.kairos.ai.dto.AiModel
import io.github.drawjustin.kairos.ai.dto.AiProvider
import io.github.drawjustin.kairos.ai.dto.AnthropicMessage
import io.github.drawjustin.kairos.ai.dto.AnthropicMessageRequest
import io.github.drawjustin.kairos.ai.dto.AnthropicMessageResponse
import io.github.drawjustin.kairos.ai.dto.ChatChoiceResponse
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageResponse
import io.github.drawjustin.kairos.ai.dto.ChatUsageResponse
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
) : ProviderAdapter {
    private val restClient = restClientBuilder.build()

    override fun supports(model: AiModel): Boolean = model.provider == AiProvider.CLAUDE

    override fun chatCompletion(request: ChatCompletionRequest): ChatCompletionResponse {
        val apiKey = anthropicProperties.apiKey.trim()
        if (apiKey.isBlank()) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_NOT_CONFIGURED)
        }

        return try {
            val response = restClient.post()
                .uri("${anthropicProperties.baseUrl.trimEnd('/')}/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", anthropicProperties.version)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request.toAnthropicRequest())
                .retrieve()
                .body<AnthropicMessageResponse>()
                ?: throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR)

            response.toChatCompletionResponse()
        } catch (exception: KairosException) {
            throw exception
        } catch (exception: Exception) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR, exception.message ?: KairosErrorCode.AI_PROVIDER_ERROR.message)
        }
    }

    private fun ChatCompletionRequest.toAnthropicRequest(): AnthropicMessageRequest {
        val systemPrompt = messages
            .filter { it.role == "system" }
            .joinToString("\n\n") { it.content }
            .ifBlank { null }
        val conversationMessages = messages
            .filterNot { it.role == "system" }
            .map {
                AnthropicMessage(
                    role = it.role,
                    content = it.content,
                )
            }

        return AnthropicMessageRequest(
            model = model.value,
            maxTokens = maxTokens ?: DEFAULT_MAX_TOKENS,
            messages = conversationMessages,
            system = systemPrompt,
            temperature = temperature,
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
                        role = "assistant",
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
