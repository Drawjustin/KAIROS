package io.github.drawjustin.kairos.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.context.dto.ContextSearchRequest
import io.github.drawjustin.kairos.context.dto.ContextSearchResult
import io.github.drawjustin.kairos.context.dto.ContextSourceCatalogItem
import io.github.drawjustin.kairos.context.service.ContextSearchService
import io.github.drawjustin.kairos.context.type.ContextSourceType
import io.github.drawjustin.kairos.mcp.service.McpService
import io.github.drawjustin.kairos.platform.dto.ProjectOutput
import io.github.drawjustin.kairos.project.type.ProjectEnvironment
import io.github.drawjustin.kairos.project.type.ProjectStatus
import io.github.drawjustin.kairos.user.type.UserRole
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class McpServiceTests {
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val contextSearchService = mock(ContextSearchService::class.java)
    private val service = McpService(
        contextSearchService = contextSearchService,
        objectMapper = objectMapper,
    )
    private val principal = AuthenticatedUser(id = 1, email = "tester@example.com", role = UserRole.ADMIN)

    @Test
    fun `initialize returns MCP server metadata`() {
        val request = objectMapper.readTree(
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "initialize",
              "params": { "protocolVersion": "2025-03-26" }
            }
            """.trimIndent(),
        )

        val response = service.handle(principal, request)

        assertThat(response?.path("jsonrpc")?.asText()).isEqualTo("2.0")
        assertThat(response?.path("id")?.asInt()).isEqualTo(1)
        assertThat(response?.path("result")?.path("serverInfo")?.path("name")?.asText()).isEqualTo("kairos-mcp")
        assertThat(response?.path("result")?.path("capabilities")?.has("tools")).isTrue()
    }

    @Test
    fun `tools list exposes context tools`() {
        val request = objectMapper.readTree(
            """
            {
              "jsonrpc": "2.0",
              "id": "tools",
              "method": "tools/list"
            }
            """.trimIndent(),
        )

        val response = service.handle(principal, request)
        val tools = response?.path("result")?.path("tools")

        assertThat(tools).hasSize(3)
        assertThat(tools?.map { it.path("name").asText() }).containsExactly(
            "kairos_list_projects",
            "kairos_list_context_sources",
            "kairos_search_context",
        )
        assertThat(tools?.get(1)?.path("inputSchema")?.path("required")?.map { it.asText() }).contains("projectId")
    }

    @Test
    fun `tools call lists accessible projects`() {
        given(contextSearchService.listProjects(principal)).willReturn(
            listOf(
                ProjectOutput(
                    id = 10,
                    tenantId = 1,
                    name = "KAIROS",
                    environment = ProjectEnvironment.OPER,
                    status = ProjectStatus.ACTIVE,
                    createdAt = Instant.parse("2026-04-27T00:00:00Z"),
                ),
            ),
        )
        val request = objectMapper.readTree(
            """
            {
              "jsonrpc": "2.0",
              "id": "projects",
              "method": "tools/call",
              "params": {
                "name": "kairos_list_projects",
                "arguments": {}
              }
            }
            """.trimIndent(),
        )

        val response = service.handle(principal, request)
        val content = response?.path("result")?.path("content")?.single()

        assertThat(content?.path("type")?.asText()).isEqualTo("text")
        assertThat(content?.path("text")?.asText()).contains("KAIROS")
        assertThat(content?.path("text")?.asText()).contains("\"id\" : 10")
    }

    @Test
    fun `tools call lists context sources`() {
        given(contextSearchService.listSources(principal, 10)).willReturn(
            listOf(
                ContextSourceCatalogItem(
                    id = 3,
                    name = "dev_docs_search_3",
                    type = ContextSourceType.MCP_SERVER,
                    description = "개발 문서를 검색한다.",
                ),
            ),
        )
        val request = objectMapper.readTree(
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "method": "tools/call",
              "params": {
                "name": "kairos_list_context_sources",
                "arguments": { "projectId": 10 }
              }
            }
            """.trimIndent(),
        )

        val response = service.handle(principal, request)
        val content = response?.path("result")?.path("content")?.single()

        assertThat(content?.path("type")?.asText()).isEqualTo("text")
        assertThat(content?.path("text")?.asText()).contains("dev_docs_search_3")
    }

    @Test
    fun `tools call searches context`() {
        given(
            contextSearchService.search(
                principal,
                ContextSearchRequest(
                    projectId = 10,
                    query = "API 응답 규칙",
                    contextSourceIds = listOf(3),
                ),
            ),
        ).willReturn(
            listOf(
                ContextSearchResult(
                    title = "API Response Convention",
                    content = "모든 API 응답은 BaseOutput을 상속한다.",
                    source = "engineering-handbook",
                    score = 3,
                    contextSourceId = 3,
                    contextSourceName = "dev_docs_search_3",
                    contextSourceType = ContextSourceType.MCP_SERVER,
                ),
            ),
        )
        val request = objectMapper.readTree(
            """
            {
              "jsonrpc": "2.0",
              "id": 3,
              "method": "tools/call",
              "params": {
                "name": "kairos_search_context",
                "arguments": {
                  "projectId": 10,
                  "query": "API 응답 규칙",
                  "contextSourceIds": [3]
                }
              }
            }
            """.trimIndent(),
        )

        val response = service.handle(principal, request)
        val content = response?.path("result")?.path("content")?.single()

        assertThat(content?.path("text")?.asText()).contains("API Response Convention")
        assertThat(content?.path("text")?.asText()).contains("engineering-handbook")
    }
}
