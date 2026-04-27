package io.github.drawjustin.kairos.mcp.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.context.dto.ContextSearchRequest
import io.github.drawjustin.kairos.context.service.ContextSearchService
import org.springframework.stereotype.Service

@Service
// KAIROS의 context 정책 계층을 MCP JSON-RPC tool 호출로 얇게 노출한다.
class McpService(
    private val contextSearchService: ContextSearchService,
    private val objectMapper: ObjectMapper,
) {
    fun handle(principal: AuthenticatedUser, request: JsonNode): JsonNode? {
        if (!request.isObject) {
            return errorResponse(null, INVALID_REQUEST, "Invalid Request")
        }

        val id = request.get("id")
        val method = request.path("method").asText(null)
        val params = request.get("params")

        return try {
            when (method) {
                "initialize" -> successResponse(id, initializeResult(params))
                "notifications/initialized", "initialized" -> null
                "ping" -> successResponse(id, objectMapper.createObjectNode())
                "tools/list" -> successResponse(id, toolsListResult())
                "tools/call" -> successResponse(id, callTool(principal, params))
                else -> errorResponse(id, METHOD_NOT_FOUND, "Method not found: $method")
            }
        } catch (exception: IllegalArgumentException) {
            errorResponse(id, INVALID_PARAMS, exception.message ?: "Invalid params")
        } catch (exception: Exception) {
            errorResponse(id, INTERNAL_ERROR, exception.message ?: "Internal error")
        }
    }

    private fun initializeResult(params: JsonNode?): ObjectNode = objectMapper.createObjectNode().apply {
        put("protocolVersion", params?.path("protocolVersion")?.asText("2025-03-26") ?: "2025-03-26")
        set<ObjectNode>("capabilities", objectMapper.createObjectNode().set("tools", objectMapper.createObjectNode()))
        set<ObjectNode>(
            "serverInfo",
            objectMapper.createObjectNode()
                .put("name", "kairos-mcp")
                .put("version", "0.1.0"),
        )
        put(
            "instructions",
            "KAIROS project scope와 사용자 권한을 통과한 context source 목록 조회 및 참조자료 검색을 제공한다.",
        )
    }

    private fun toolsListResult(): ObjectNode = objectMapper.createObjectNode().apply {
        set<ArrayNode>(
            "tools",
            objectMapper.createArrayNode()
                .add(listProjectsTool())
                .add(listContextSourcesTool())
                .add(searchContextTool()),
        )
    }

    private fun listProjectsTool(): ObjectNode = objectMapper.createObjectNode().apply {
        put("name", LIST_PROJECTS)
        put("description", "현재 인증된 사용자가 접근할 수 있는 KAIROS project 목록을 조회한다.")
        set<ObjectNode>(
            "inputSchema",
            objectSchema(
                properties = emptyMap(),
                required = emptyList(),
            ),
        )
    }

    private fun listContextSourcesTool(): ObjectNode = objectMapper.createObjectNode().apply {
        put("name", LIST_CONTEXT_SOURCES)
        put("description", "KAIROS project에서 사용할 수 있는 context source 목록을 조회한다.")
        set<ObjectNode>(
            "inputSchema",
            objectSchema(
                properties = mapOf(
                    "projectId" to objectMapper.createObjectNode()
                        .put("type", "number")
                        .put("description", "KAIROS project ID"),
                ),
                required = listOf("projectId"),
            ),
        )
    }

    private fun searchContextTool(): ObjectNode = objectMapper.createObjectNode().apply {
        put("name", SEARCH_CONTEXT)
        put("description", "KAIROS Context API를 통해 project scope 안에서 허용된 사내 개발 문서나 참조자료를 검색한다.")
        set<ObjectNode>(
            "inputSchema",
            objectSchema(
                properties = mapOf(
                    "projectId" to objectMapper.createObjectNode()
                        .put("type", "number")
                        .put("description", "KAIROS project ID"),
                    "query" to objectMapper.createObjectNode()
                        .put("type", "string")
                        .put("description", "검색할 질문 또는 검색어"),
                    "contextSourceIds" to objectMapper.createObjectNode()
                        .put("type", "array")
                        .put("description", "검색할 context source ID 목록. 생략하면 project에 연결된 모든 source를 검색한다.")
                        .set<ObjectNode>("items", objectMapper.createObjectNode().put("type", "number")),
                ),
                required = listOf("projectId", "query"),
            ),
        )
    }

    private fun objectSchema(properties: Map<String, ObjectNode>, required: List<String>): ObjectNode =
        objectMapper.createObjectNode().apply {
            put("type", "object")
            val propertiesNode = objectMapper.createObjectNode()
            properties.forEach { (name, schema) -> propertiesNode.set<ObjectNode>(name, schema) }
            set<ObjectNode>("properties", propertiesNode)
            set<ArrayNode>("required", objectMapper.createArrayNode().also { required.forEach(it::add) })
        }

    private fun callTool(principal: AuthenticatedUser, params: JsonNode?): ObjectNode {
        val name = params?.path("name")?.asText(null)
            ?: throw IllegalArgumentException("Tool name is required")
        val arguments = params.get("arguments") ?: objectMapper.createObjectNode()

        val result = when (name) {
            LIST_PROJECTS -> contextSearchService.listProjects(principal)

            LIST_CONTEXT_SOURCES -> contextSearchService.listSources(
                principal = principal,
                projectId = arguments.requiredProjectId(),
            )

            SEARCH_CONTEXT -> contextSearchService.search(
                principal = principal,
                request = ContextSearchRequest(
                    projectId = arguments.requiredProjectId(),
                    query = arguments.requiredText("query"),
                    contextSourceIds = arguments.path("contextSourceIds")
                        .takeIf { it.isArray }
                        ?.map { it.asLong() },
                ),
            )

            else -> throw IllegalArgumentException("Unknown tool: $name")
        }

        return objectMapper.createObjectNode().apply {
            set<ArrayNode>(
                "content",
                objectMapper.createArrayNode().add(
                    objectMapper.createObjectNode()
                        .put("type", "text")
                        .put("text", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result)),
                ),
            )
        }
    }

    private fun JsonNode.requiredProjectId(): Long {
        val projectId = path("projectId").asLong(0)
        require(projectId > 0) { "projectId is required" }
        return projectId
    }

    private fun JsonNode.requiredText(field: String): String {
        val value = path(field).asText("").trim()
        require(value.isNotBlank()) { "$field is required" }
        return value
    }

    private fun successResponse(id: JsonNode?, result: JsonNode): ObjectNode = objectMapper.createObjectNode().apply {
        put("jsonrpc", "2.0")
        set<JsonNode>("id", id ?: objectMapper.nullNode())
        set<JsonNode>("result", result)
    }

    private fun errorResponse(id: JsonNode?, code: Int, message: String): ObjectNode = objectMapper.createObjectNode().apply {
        put("jsonrpc", "2.0")
        set<JsonNode>("id", id ?: objectMapper.nullNode())
        set<ObjectNode>(
            "error",
            objectMapper.createObjectNode()
                .put("code", code)
                .put("message", message),
        )
    }

    private companion object {
        const val LIST_PROJECTS = "kairos_list_projects"
        const val LIST_CONTEXT_SOURCES = "kairos_list_context_sources"
        const val SEARCH_CONTEXT = "kairos_search_context"
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
    }
}
