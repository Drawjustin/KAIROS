package io.github.drawjustin.kairos.ai.provider.claude

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.drawjustin.kairos.ai.tool.AiToolParameters

// null 필드는 아예 보내지 않는다.
// Anthropic은 tools를 배열로만 받아서 "tools": null을 보내면 요청 전체를 거부한다.
// 도구가 연결되지 않은 project는 tools가 비는데, 그 상태에서 Claude 호출이 전부 실패했다.
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AnthropicMessageRequest(
    val model: String,
    @JsonProperty("max_tokens")
    val maxTokens: Int,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val temperature: Double? = null,
    val tools: List<AnthropicTool>? = null,
)

data class AnthropicMessage(
    val role: String,
    val content: Any,
)

data class AnthropicTool(
    val name: String,
    val description: String,
    @JsonProperty("input_schema")
    val inputSchema: AiToolParameters,
)

data class AnthropicMessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicContentBlock>,
    val model: String,
    @JsonProperty("stop_reason")
    val stopReason: String?,
    val usage: AnthropicUsage,
)

data class AnthropicContentBlock(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any?>? = null,
)

data class AnthropicUsage(
    @JsonProperty("input_tokens")
    val inputTokens: Int,
    @JsonProperty("output_tokens")
    val outputTokens: Int,
)
