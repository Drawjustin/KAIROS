package io.github.drawjustin.kairos.ai.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class OpenAiChatCompletionRequest(
    val model: String,
    @JsonProperty("max_tokens")
    val maxTokens: Int? = null,
    val messages: List<OpenAiChatMessage>,
    val temperature: Double? = null,
    val stream: Boolean = false,
)

data class OpenAiChatMessage(
    val role: String,
    val content: String,
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
    val content: String?,
)

data class OpenAiChatUsage(
    @JsonProperty("prompt_tokens")
    val promptTokens: Int,
    @JsonProperty("completion_tokens")
    val completionTokens: Int,
    @JsonProperty("total_tokens")
    val totalTokens: Int,
)
