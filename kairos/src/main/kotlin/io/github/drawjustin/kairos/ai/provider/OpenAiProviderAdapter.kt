package io.github.drawjustin.kairos.ai.provider

import io.github.drawjustin.kairos.ai.config.OpenAiProperties
import io.github.drawjustin.kairos.ai.dto.AiModel
import io.github.drawjustin.kairos.ai.dto.AiProvider
import io.github.drawjustin.kairos.ai.dto.ChatChoiceResponse
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.dto.ChatMessageResponse
import io.github.drawjustin.kairos.ai.dto.ChatUsageResponse
import io.github.drawjustin.kairos.ai.provider.openai.OpenAiChatChoice
import io.github.drawjustin.kairos.ai.provider.openai.OpenAiChatCompletionRequest
import io.github.drawjustin.kairos.ai.provider.openai.OpenAiChatCompletionResponse
import io.github.drawjustin.kairos.ai.provider.openai.OpenAiChatMessage
import io.github.drawjustin.kairos.ai.provider.openai.OpenAiChatMessageResponse
import io.github.drawjustin.kairos.ai.provider.openai.OpenAiChatUsage
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
// OpenAI Chat Completions API를 KAIROS 공통 chat completion 형식으로 연결한다.
class OpenAiProviderAdapter(
    private val openAiProperties: OpenAiProperties,
    restClientBuilder: RestClient.Builder,
) : ProviderAdapter {
    private val restClient = restClientBuilder.build()

    override fun supports(model: AiModel): Boolean = model.provider == AiProvider.OPENAI

    override fun chatCompletion(request: ChatCompletionRequest): ChatCompletionResponse {
        val apiKey = openAiProperties.apiKey.trim()
        if (apiKey.isBlank()) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_NOT_CONFIGURED)
        }

        return try {
            val response = restClient.post()
                .uri("${openAiProperties.baseUrl.trimEnd('/')}/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request.toOpenAiRequest())
                .retrieve()
                .body<OpenAiChatCompletionResponse>()
                ?: throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR)

            response.toChatCompletionResponse()
        } catch (exception: KairosException) {
            throw exception
        } catch (exception: Exception) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR, exception.message ?: KairosErrorCode.AI_PROVIDER_ERROR.message)
        }
    }

    private fun ChatCompletionRequest.toOpenAiRequest(): OpenAiChatCompletionRequest =
        OpenAiChatCompletionRequest(
            model = model.value,
            messages = messages.map {
                OpenAiChatMessage(
                    role = it.role,
                    content = it.content,
                )
            },
            temperature = temperature,
            maxTokens = maxTokens,
            stream = stream,
        )

        private fun OpenAiChatCompletionResponse.toChatCompletionResponse(): ChatCompletionResponse =
            ChatCompletionResponse(
                id = id,
                `object` = `object`,
                created = created,
                model = model,
                choices = choices.map { it.toChatChoiceResponse() },
                usage = usage?.toChatUsageResponse(),
            )

    private fun OpenAiChatChoice.toChatChoiceResponse(): ChatChoiceResponse =
        ChatChoiceResponse(
            index = index,
            message = message.toChatMessageResponse(),
            finishReason = finishReason,
        )

    private fun OpenAiChatMessageResponse.toChatMessageResponse(): ChatMessageResponse =
        ChatMessageResponse(
            role = role,
            content = content,
        )

    private fun OpenAiChatUsage.toChatUsageResponse(): ChatUsageResponse =
        ChatUsageResponse(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
        )
}
