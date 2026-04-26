package io.github.drawjustin.kairos.ai.tool

import io.github.drawjustin.kairos.context.type.ContextSourceType

// provider별 tool calling 형식으로 바꾸기 전, KAIROS 내부에서 공통으로 쓰는 tool 정의다.
data class AiToolDefinition(
    val sourceId: Long,
    val name: String,
    val description: String,
    val sourceType: ContextSourceType,
    val sourceUri: String,
    val parameters: AiToolParameters = AiToolParameters.defaultSearchQuery(),
)

data class AiToolParameters(
    val type: String,
    val properties: Map<String, AiToolParameterProperty>,
    val required: List<String>,
) {
    companion object {
        fun defaultSearchQuery(): AiToolParameters = AiToolParameters(
            type = "object",
            properties = mapOf(
                "query" to AiToolParameterProperty(
                    type = "string",
                    description = "사용자 질문에서 추출한 검색어",
                ),
            ),
            required = listOf("query"),
        )
    }
}

data class AiToolParameterProperty(
    val type: String,
    val description: String,
)
