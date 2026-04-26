package io.github.drawjustin.kairos.ai.provider

import io.github.drawjustin.kairos.ai.config.OpenAiProperties
import io.github.drawjustin.kairos.ai.type.AiModel
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
import io.github.drawjustin.kairos.ai.provider.openai.OpenAiTool
import io.github.drawjustin.kairos.ai.provider.openai.OpenAiToolCall
import io.github.drawjustin.kairos.ai.provider.openai.OpenAiToolFunction
import io.github.drawjustin.kairos.ai.service.AiToolExecutor
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.ai.type.AiProvider
import io.github.drawjustin.kairos.ai.type.ChatRole
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
    private val aiToolExecutor: AiToolExecutor,
) : ProviderAdapter {
    private val restClient = restClientBuilder.build()

    override fun supports(model: AiModel): Boolean = model.provider == AiProvider.OPENAI

    override fun chatCompletion(request: ChatCompletionRequest, tools: List<AiToolDefinition>): ChatCompletionResponse {
        val apiKey = openAiProperties.apiKey.trim()
        if (apiKey.isBlank()) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_NOT_CONFIGURED)
        }

        return try {
            val messages = request.toProviderMessages()
            val response = sendProviderRequest(
                apiKey = apiKey,
                request = request.toProviderRequest(messages = messages, tools = tools),
            )
            val pendingToolCalls = response.pendingToolCalls()
            if (pendingToolCalls.isEmpty()) {
                response.toChatCompletionResponse()
            } else {
                val followUpMessages = messages +
                    response.assistantToolCallMessage() +
                    pendingToolCalls.map { it.toProviderToolResult(tools) }
                sendProviderRequest(
                    apiKey = apiKey,
                    request = request.toProviderRequest(messages = followUpMessages, tools = tools),
                ).toChatCompletionResponse()
            }
        } catch (exception: KairosException) {
            throw exception
        } catch (exception: Exception) {
            throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR, exception.message ?: KairosErrorCode.AI_PROVIDER_ERROR.message)
        }
    }

    private fun sendProviderRequest(
        apiKey: String,
        request: OpenAiChatCompletionRequest,
    ): OpenAiChatCompletionResponse =
        restClient.post()
            .uri("${openAiProperties.baseUrl.trimEnd('/')}/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body<OpenAiChatCompletionResponse>()
            ?: throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR)

    private fun ChatCompletionRequest.toProviderRequest(
        messages: List<OpenAiChatMessage>,
        tools: List<AiToolDefinition>,
    ): OpenAiChatCompletionRequest =
        OpenAiChatCompletionRequest(
            model = model.value,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = stream,
            tools = tools.toProviderTools().takeIf { it.isNotEmpty() },
            toolChoice = "auto".takeIf { tools.isNotEmpty() },
        )

    private fun ChatCompletionRequest.toProviderMessages(): List<OpenAiChatMessage> =
        messages.map {
            OpenAiChatMessage(
                role = it.role.value,
                content = it.content,
            )
        }

    private fun List<AiToolDefinition>.toProviderTools(): List<OpenAiTool> =
        map {
            OpenAiTool(
                function = OpenAiToolFunction(
                    name = it.name,
                    description = it.description,
                    parameters = it.parameters,
                ),
            )
        }

    private fun OpenAiChatCompletionResponse.pendingToolCalls(): List<OpenAiToolCall> =
        choices.firstOrNull()?.message?.toolCalls.orEmpty()

    private fun OpenAiChatCompletionResponse.assistantToolCallMessage(): OpenAiChatMessage {
        val message = choices.firstOrNull()?.message ?: throw KairosException(KairosErrorCode.AI_PROVIDER_ERROR)
        return OpenAiChatMessage(
            role = ChatRole.ASSISTANT.value,
            content = message.content,
            toolCalls = message.toolCalls,
        )
    }

    private fun OpenAiToolCall.toProviderToolResult(tools: List<AiToolDefinition>): OpenAiChatMessage {
        val tool = tools.firstOrNull { it.name == function.name }
            ?: throw KairosException(KairosErrorCode.AI_TOOL_NOT_ALLOWED)
        val result = aiToolExecutor.execute(tool = tool, arguments = function.arguments)
        return OpenAiChatMessage(
            role = "tool",
            content = result,
            toolCallId = id,
        )
    }

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
            role = ChatRole.fromValue(role),
            content = content,
        )

    private fun OpenAiChatUsage.toChatUsageResponse(): ChatUsageResponse =
        ChatUsageResponse(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
        )
}
