package io.github.drawjustin.kairos.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
// AI가 선택한 tool을 실제 내부 문서 검색 서버/MCP gateway로 실행한다.
class AiToolExecutor(
    private val objectMapper: ObjectMapper,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.build()

    fun execute(tool: AiToolDefinition, arguments: String?): String {
        val query = parseQuery(arguments)
        return try {
            restClient.post()
                .uri(tool.sourceUri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(AiToolExecutionRequest(query = query))
                .retrieve()
                .body<String>()
                ?: throw KairosException(KairosErrorCode.AI_TOOL_EXECUTION_FAILED)
        } catch (exception: KairosException) {
            throw exception
        } catch (exception: Exception) {
            throw KairosException(
                KairosErrorCode.AI_TOOL_EXECUTION_FAILED,
                exception.message ?: KairosErrorCode.AI_TOOL_EXECUTION_FAILED.message,
            )
        }
    }

    private fun parseQuery(arguments: String?): String {
        val query = try {
            objectMapper.readTree(arguments.orEmpty()).path("query").asText().trim()
        } catch (exception: Exception) {
            throw KairosException(KairosErrorCode.AI_TOOL_EXECUTION_FAILED, "Invalid tool arguments")
        }
        if (query.isBlank()) {
            throw KairosException(KairosErrorCode.AI_TOOL_EXECUTION_FAILED, "Tool query is required")
        }
        return query
    }
}

data class AiToolExecutionRequest(
    val query: String,
)
