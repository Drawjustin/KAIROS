package io.github.drawjustin.kairos.ai.provider.openai

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.drawjustin.kairos.ai.tool.AiToolParameters

data class OpenAiChatCompletionRequest(
    val model: String,
    @JsonProperty("max_tokens")
    val maxTokens: Int? = null,
    val messages: List<OpenAiChatMessage>,
    val temperature: Double? = null,
    val stream: Boolean = false,
    val tools: List<OpenAiTool>? = null,
    @JsonProperty("tool_choice")
    val toolChoice: String? = null,
)

data class OpenAiChatMessage(
    val role: String,
    val content: String? = null,
    @JsonProperty("tool_calls")
    val toolCalls: List<OpenAiToolCall>? = null,
    @JsonProperty("tool_call_id")
    val toolCallId: String? = null,
)

data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiToolFunction,
)

data class OpenAiToolFunction(
    val name: String,
    val description: String,
    val parameters: AiToolParameters,
)

data class OpenAiChatCompletionResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiChatChoice>,
    val usage: OpenAiChatUsage? = null,
)

data class OpenAiChatChoice(
    val index: Int,
    val message: OpenAiChatMessageResponse,
    @JsonProperty("finish_reason")
    val finishReason: String? = null,
)

data class OpenAiChatMessageResponse(
    val role: String,
    val content: String? = null,
    @JsonProperty("tool_calls")
    val toolCalls: List<OpenAiToolCall>? = null,
)

data class OpenAiToolCall(
    val id: String,
    val type: String,
    val function: OpenAiToolCallFunction,
)

data class OpenAiToolCallFunction(
    val name: String,
    val arguments: String? = null,
)

data class OpenAiChatUsage(
    @JsonProperty("prompt_tokens")
    val promptTokens: Int,
    @JsonProperty("completion_tokens")
    val completionTokens: Int,
    @JsonProperty("total_tokens")
    val totalTokens: Int,
)
