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
                    description = "검색할 정책명, 업무 키워드, 이슈 키워드를 짧은 한국어 검색어로 작성한다.",
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
