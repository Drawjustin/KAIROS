package io.github.drawjustin.kairos.ai.type

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class ChatRole(
    @get:JsonValue
    val value: String,
) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): ChatRole =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unsupported chat role: $value")
    }
}
