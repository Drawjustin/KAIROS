package io.github.drawjustin.kairos.ai.type

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

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
