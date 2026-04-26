package io.github.drawjustin.kairos.ai.provider

import io.github.drawjustin.kairos.ai.config.GeminiProperties
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.dto.ChatChoiceResponse
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageResponse
import io.github.drawjustin.kairos.ai.dto.ChatUsageResponse
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiCandidate
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiContent
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiGenerateContentRequest
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiGenerateContentResponse
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiGenerationConfig
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiPart
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiUsageMetadata
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
// Gemini Generate Content API 형식을 KAIROS 공통 chat completion 형식으로 변환한다.
class GeminiProviderAdapter(
    private val geminiProperties: GeminiProperties,
    restClientBuilder: RestClient.Builder,
) : ProviderAdapter {
    private val restClient = restClientBuilder.build()

    override fun supports(model: AiModel): Boolean = model.provider == AiProvider.GEMINI

    override fun chatCompletion(request: ChatCompletionRequest, tools: List<AiToolDefinition>): ChatCompletionResponse {
        val apiKey = geminiProperties.apiKey.trim()
        if (apiKey.isBlank()) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_NOT_CONFIGURED)
        }

        return try {
            val response = restClient.post()
                .uri("${geminiProperties.baseUrl.trimEnd('/')}/v1beta/models/${request.model.value}:generateContent")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request.toGeminiRequest())
                .retrieve()
                .body<GeminiGenerateContentResponse>()
                ?: throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR)

            response.toChatCompletionResponse(request.model.value)
        } catch (exception: KairosException) {
            throw exception
        } catch (exception: Exception) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR, exception.message ?: KairosErrorCode.AI_PROVIDER_ERROR.message)
        }
    }

    private fun ChatCompletionRequest.toGeminiRequest(): GeminiGenerateContentRequest {
        val systemPrompt = messages
            .filter { it.role == ChatRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .ifBlank { null }
        val conversationContents = messages
            .filterNot { it.role == ChatRole.SYSTEM }
            .map {
                GeminiContent(
                    role = it.role.toGeminiRole(),
                    parts = listOf(GeminiPart(text = it.content)),
                )
            }

        return GeminiGenerateContentRequest(
            contents = conversationContents,
            systemInstruction = systemPrompt?.let {
                GeminiContent(parts = listOf(GeminiPart(text = it)))
            },
            generationConfig = GeminiGenerationConfig(
                temperature = temperature,
                maxOutputTokens = maxTokens,
            ),
        )
    }

    private fun ChatRole.toGeminiRole(): String =
        when (this) {
            ChatRole.ASSISTANT -> "model"
            else -> value
        }

    private fun GeminiGenerateContentResponse.toChatCompletionResponse(requestedModel: String): ChatCompletionResponse =
        ChatCompletionResponse(
            id = responseId ?: "gemini_${Instant.now().toEpochMilli()}",
            `object` = "chat.completion",
            created = Instant.now().epochSecond,
            model = modelVersion ?: requestedModel,
            choices = candidates.map { it.toChatChoiceResponse() },
            usage = usageMetadata?.toChatUsageResponse(),
        )

    private fun GeminiCandidate.toChatChoiceResponse(): ChatChoiceResponse =
        ChatChoiceResponse(
            index = index ?: 0,
            message = ChatMessageResponse(
                role = ChatRole.ASSISTANT,
                content = content?.parts.orEmpty().joinToString("") { it.text },
            ),
            finishReason = finishReason,
        )

    private fun GeminiUsageMetadata.toChatUsageResponse(): ChatUsageResponse =
        ChatUsageResponse(
            promptTokens = promptTokenCount ?: 0,
            completionTokens = candidatesTokenCount ?: 0,
            totalTokens = totalTokenCount ?: ((promptTokenCount ?: 0) + (candidatesTokenCount ?: 0)),
        )
}
