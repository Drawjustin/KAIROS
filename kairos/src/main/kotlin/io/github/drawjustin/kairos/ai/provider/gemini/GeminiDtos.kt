package io.github.drawjustin.kairos.ai.provider.gemini

data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiTool>? = null,
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null,
)

data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
)

data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>,
)

data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: GeminiFunctionParameters,
)

data class GeminiFunctionParameters(
    val type: String,
    val properties: Map<String, GeminiFunctionParameterProperty>,
    val required: List<String>,
)

data class GeminiFunctionParameterProperty(
    val type: String,
    val description: String,
)

data class GeminiFunctionCall(
    val name: String,
    val args: Map<String, Any?> = emptyMap(),
)

data class GeminiFunctionResponse(
    val name: String,
    val response: Map<String, Any?>,
)

data class GeminiGenerateContentResponse(
    val responseId: String? = null,
    val modelVersion: String? = null,
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: GeminiUsageMetadata? = null,
)

data class GeminiCandidate(
    val index: Int? = null,
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
)
