package io.github.drawjustin.kairos.ai.provider

import com.fasterxml.jackson.databind.ObjectMapper
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
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiFunctionCall
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiFunctionDeclaration
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiFunctionParameterProperty
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiFunctionParameters
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiFunctionResponse
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiPart
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiTool
import io.github.drawjustin.kairos.ai.provider.gemini.GeminiUsageMetadata
import io.github.drawjustin.kairos.ai.service.AiToolExecutor
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
    private val aiToolExecutor: AiToolExecutor,
    private val objectMapper: ObjectMapper,
) : ProviderAdapter {
    private val restClient = restClientBuilder.build()

    override fun supports(model: AiModel): Boolean = model.provider == AiProvider.GEMINI

    override fun chatCompletion(request: ChatCompletionRequest, tools: List<AiToolDefinition>): ChatCompletionResponse {
        val apiKey = geminiProperties.apiKey.trim()
        if (apiKey.isBlank()) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_NOT_CONFIGURED)
        }

        return try {
            val providerRequest = request.toProviderRequest(tools = tools)
            val response = sendProviderRequest(apiKey = apiKey, model = request.model.value, request = providerRequest)
            val pendingToolCalls = response.pendingToolCalls()
            if (pendingToolCalls.isEmpty()) {
                response.toChatCompletionResponse(request.model.value)
            } else {
                val modelContent = response.candidates.firstOrNull()?.content
                    ?: throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR)
                val toolResultContent = GeminiContent(
                    role = "function",
                    parts = pendingToolCalls.map { it.toProviderToolResult(tools) },
                )
                sendProviderRequest(
                    apiKey = apiKey,
                    model = request.model.value,
                    request = providerRequest.copy(contents = providerRequest.contents + modelContent + toolResultContent),
                ).toChatCompletionResponse(request.model.value)
            }
        } catch (exception: KairosException) {
            throw exception
        } catch (exception: Exception) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR, exception.message ?: KairosErrorCode.AI_PROVIDER_ERROR.message)
        }
    }

    private fun sendProviderRequest(
        apiKey: String,
        model: String,
        request: GeminiGenerateContentRequest,
    ): GeminiGenerateContentResponse =
        restClient.post()
            .uri("${geminiProperties.baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body<GeminiGenerateContentResponse>()
            ?: throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR)

    private fun ChatCompletionRequest.toProviderRequest(tools: List<AiToolDefinition>): GeminiGenerateContentRequest {
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
            tools = tools.toProviderTools().takeIf { it.isNotEmpty() },
        )
    }

    private fun List<AiToolDefinition>.toProviderTools(): List<GeminiTool> =
        listOf(
            GeminiTool(
                functionDeclarations = map {
                    GeminiFunctionDeclaration(
                        name = it.name,
                        description = it.description,
                        parameters = GeminiFunctionParameters(
                            type = it.parameters.type.uppercase(),
                            properties = it.parameters.properties.mapValues { property ->
                                GeminiFunctionParameterProperty(
                                    type = property.value.type.uppercase(),
                                    description = property.value.description,
                                )
                            },
                            required = it.parameters.required,
                        ),
                    )
                },
            ),
        )

    private fun GeminiGenerateContentResponse.pendingToolCalls(): List<GeminiFunctionCall> =
        candidates.firstOrNull()?.content?.parts.orEmpty().mapNotNull { it.functionCall }

    private fun GeminiFunctionCall.toProviderToolResult(tools: List<AiToolDefinition>): GeminiPart {
        val tool = tools.firstOrNull { it.name == name }
            ?: throw KairosException(KairosErrorCode.AI_TOOL_NOT_ALLOWED)
        val result = aiToolExecutor.execute(
            tool = tool,
            arguments = objectMapper.writeValueAsString(args),
        )
        return GeminiPart(
            functionResponse = GeminiFunctionResponse(
                name = name,
                response = mapOf("result" to result),
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
                content = content?.parts.orEmpty().joinToString("") { it.text.orEmpty() },
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
