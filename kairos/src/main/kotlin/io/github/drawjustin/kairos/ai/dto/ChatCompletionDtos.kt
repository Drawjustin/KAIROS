package io.github.drawjustin.kairos.ai.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.github.drawjustin.kairos.ai.type.AiProvider
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class ChatCompletionRequest(
    @field:Schema(description = "호출할 모델 이름", example = "gpt-4o-mini")
    val model: AiModel,
    @field:Schema(description = "대화 메시지 목록")
    @field:NotEmpty
    @field:Valid
    val messages: List<ChatMessageRequest>,
    @field:Schema(description = "샘플링 온도", example = "0.7")
    @field:DecimalMin("0.0")
    @field:DecimalMax("2.0")
    val temperature: Double? = null,
    @field:Schema(description = "최대 출력 토큰 수", example = "512")
    @field:Min(1)
    @field:JsonProperty("max_tokens")
    val maxTokens: Int? = null,
    @field:Schema(description = "스트리밍 여부. 최소 버전에서는 false만 지원", example = "false")
    val stream: Boolean = false,
)

data class ChatMessageRequest(
    @field:Schema(description = "메시지 역할", example = "user")
    val role: ChatRole,
    @field:Schema(description = "메시지 텍스트", example = "안녕하세요")
    @field:NotBlank
    @field:Size(max = 20_000)
    val content: String,
)

data class ChatCompletionResponse(
    @field:Schema(description = "응답 ID", example = "chatcmpl_123")
    val id: String,
    @field:Schema(description = "응답 객체 타입", example = "chat.completion")
    val `object`: String,
    @field:Schema(description = "생성 시각 epoch seconds", example = "1713086400")
    val created: Long,
    @field:Schema(description = "실제로 사용된 모델", example = "gpt-4o-mini")
    val model: String,
    @field:Schema(description = "선택지 목록")
    val choices: List<ChatChoiceResponse>,
    @field:Schema(description = "토큰 사용량")
    val usage: ChatUsageResponse? = null,
)

data class ChatChoiceResponse(
    @field:Schema(description = "선택지 인덱스", example = "0")
    val index: Int,
    @field:Schema(description = "모델 응답 메시지")
    val message: ChatMessageResponse,
    @field:Schema(description = "종료 이유", example = "stop")
    @field:JsonProperty("finish_reason")
    val finishReason: String? = null,
)

data class ChatMessageResponse(
    @field:Schema(description = "메시지 역할", example = "assistant")
    val role: ChatRole,
    @field:Schema(description = "메시지 텍스트", example = "안녕하세요. 무엇을 도와드릴까요?")
    val content: String?,
)

data class ChatUsageResponse(
    @field:Schema(description = "프롬프트 토큰 수", example = "12")
    @field:JsonProperty("prompt_tokens")
    val promptTokens: Int,
    @field:Schema(description = "생성 토큰 수", example = "18")
    @field:JsonProperty("completion_tokens")
    val completionTokens: Int,
    @field:Schema(description = "총 토큰 수", example = "30")
    @field:JsonProperty("total_tokens")
    val totalTokens: Int,
)

enum class AiModel(
    @get:JsonValue
    val value: String,
    val provider: AiProvider,
) {
    GPT_4O_MINI("gpt-4o-mini", AiProvider.OPENAI),
    GPT_4O("gpt-4o", AiProvider.OPENAI),
    GEMINI_2_5_FLASH("gemini-2.5-flash", AiProvider.GEMINI),
    CLAUDE_OPUS_4_7("claude-opus-4-7", AiProvider.CLAUDE),
    CLAUDE_SONNET_4_6("claude-sonnet-4-6", AiProvider.CLAUDE),
    CLAUDE_HAIKU_4_5("claude-haiku-4-5-20251001", AiProvider.CLAUDE);

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): AiModel =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unsupported model: $value")
    }
}
