package io.github.drawjustin.kairos.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.ai.dto.AiToolExecutionRequest
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.context.service.ContextSearchLoggingService
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
// AI가 선택한 tool을 실제 내부 문서 검색 서버/MCP gateway로 실행한다.
class AiToolExecutor(
    private val objectMapper: ObjectMapper,
    private val contextSearchLoggingService: ContextSearchLoggingService,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.build()

    fun execute(
        tool: AiToolDefinition,
        arguments: String?,
        context: AiToolExecutionContext? = null,
    ): String {
        val query = try {
            parseQuery(arguments)
        } catch (exception: KairosException) {
            recordFailure(
                tool = tool,
                query = arguments.orEmpty(),
                latencyMs = 0,
                errorCode = exception.errorCode.code,
                context = context,
            )
            throw exception
        }
        return executeQuery(tool = tool, query = query, context = context)
    }

    fun executeQuery(
        tool: AiToolDefinition,
        query: String,
        context: AiToolExecutionContext? = null,
    ): String {
        val startedAt = System.nanoTime()
        val searchQuery = query.trim()
        if (searchQuery.isBlank()) {
            recordFailure(
                tool = tool,
                query = searchQuery,
                latencyMs = elapsedMillis(startedAt),
                errorCode = KairosErrorCode.AI_TOOL_EXECUTION_FAILED.code,
                context = context,
            )
            throw KairosException(KairosErrorCode.AI_TOOL_EXECUTION_FAILED, "Tool query is required")
        }
        return try {
            val response = restClient.post()
                .uri(tool.sourceUri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(AiToolExecutionRequest(query = searchQuery))
                .retrieve()
                .body<String>()
                ?: throw KairosException(KairosErrorCode.AI_TOOL_EXECUTION_FAILED)
            recordSuccess(
                tool = tool,
                query = searchQuery,
                response = response,
                latencyMs = elapsedMillis(startedAt),
                context = context,
            )
            response
        } catch (exception: KairosException) {
            recordFailure(
                tool = tool,
                query = searchQuery,
                latencyMs = elapsedMillis(startedAt),
                errorCode = exception.errorCode.code,
                context = context,
            )
            throw exception
        } catch (exception: Exception) {
            recordFailure(
                tool = tool,
                query = searchQuery,
                latencyMs = elapsedMillis(startedAt),
                errorCode = KairosErrorCode.AI_TOOL_EXECUTION_FAILED.code,
                context = context,
            )
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

    private fun recordSuccess(
        tool: AiToolDefinition,
        query: String,
        response: String,
        latencyMs: Long,
        context: AiToolExecutionContext?,
    ) {
        if (context == null) {
            return
        }
        contextSearchLoggingService.recordSuccess(
            userId = context.userId,
            project = context.project,
            purpose = context.purpose,
            query = query,
            requestedContextSourceIds = listOf(tool.sourceId),
            searchedContextSourceIds = listOf(tool.sourceId),
            resultCount = response.resultCount(),
            latencyMs = latencyMs,
        )
    }

    private fun recordFailure(
        tool: AiToolDefinition,
        query: String,
        latencyMs: Long,
        errorCode: String,
        context: AiToolExecutionContext?,
    ) {
        if (context == null) {
            return
        }
        contextSearchLoggingService.recordFailure(
            userId = context.userId,
            project = context.project,
            purpose = context.purpose,
            query = query,
            requestedContextSourceIds = listOf(tool.sourceId),
            searchedContextSourceIds = listOf(tool.sourceId),
            latencyMs = latencyMs,
            errorCode = errorCode,
        )
    }

    private fun String.resultCount(): Int =
        try {
            val documents = objectMapper.readTree(this).path("documents")
            if (documents.isArray) {
                documents.size()
            } else {
                1
            }
        } catch (exception: Exception) {
            1
        }

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}
