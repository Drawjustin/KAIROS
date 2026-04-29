package io.github.drawjustin.kairos.context

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.drawjustin.kairos.ai.service.AiToolExecutor
import io.github.drawjustin.kairos.ai.service.ProjectContextToolService
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.context.dto.ContextSearchRequest
import io.github.drawjustin.kairos.context.service.ContextSearchLoggingService
import io.github.drawjustin.kairos.context.service.ContextSearchService
import io.github.drawjustin.kairos.context.type.ContextSourceType
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.entity.Tenant
import io.github.drawjustin.kairos.tenant.repository.TenantUserRepository
import io.github.drawjustin.kairos.user.type.UserRole
import java.time.Instant
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails

class ContextSearchServiceTests {
    private val projectRepository = mock(ProjectRepository::class.java)
    private val tenantUserRepository = mock(TenantUserRepository::class.java)
    private val projectContextToolService = mock(ProjectContextToolService::class.java)
    private val aiToolExecutor = mock(AiToolExecutor::class.java)
    private val contextSearchLoggingService = mock(ContextSearchLoggingService::class.java)
    private val service = ContextSearchService(
        projectRepository = projectRepository,
        tenantUserRepository = tenantUserRepository,
        projectContextToolService = projectContextToolService,
        aiToolExecutor = aiToolExecutor,
        contextSearchLoggingService = contextSearchLoggingService,
        objectMapper = jacksonObjectMapper(),
    )

    @Test
    fun `admin can list all projects for MCP project selection`() {
        val principal = AuthenticatedUser(id = 1, email = "admin@example.com", role = UserRole.ADMIN)
        val project = Project(
            id = 10,
            tenant = Tenant(id = 1, name = "platform"),
            name = "KAIROS",
        ).apply {
            createdAt = Instant.parse("2026-04-27T00:00:00Z")
        }

        given(projectRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()).willReturn(listOf(project))

        val result = service.listProjects(principal)

        assertThat(result).hasSize(1)
        assertThat(result.single().id).isEqualTo(10)
        assertThat(result.single().name).isEqualTo("KAIROS")
        assertThat(result.single().tenantId).isEqualTo(1)
    }

    @Test
    fun `user can list only accessible projects for MCP project selection`() {
        val principal = AuthenticatedUser(id = 2, email = "dev@example.com", role = UserRole.USER)
        val project = Project(
            id = 20,
            tenant = Tenant(id = 3, name = "product"),
            name = "Wadada",
        ).apply {
            createdAt = Instant.parse("2026-04-27T00:00:00Z")
        }

        given(projectRepository.findAccessibleProjectsByUserId(2)).willReturn(listOf(project))

        val result = service.listProjects(principal)

        assertThat(result).hasSize(1)
        assertThat(result.single().id).isEqualTo(20)
        assertThat(result.single().name).isEqualTo("Wadada")
        assertThat(result.single().tenantId).isEqualTo(3)
    }

    @Test
    fun `tenant member can list project context source catalog`() {
        val principal = AuthenticatedUser(id = 2, email = "dev@example.com", role = UserRole.USER)
        val project = Project(
            id = 10,
            tenant = Tenant(id = 1, name = "dev-team"),
            name = "backend-api",
        )
        val tool = AiToolDefinition(
            sourceId = 3,
            name = "dev_docs_search_3",
            description = "API 개발 규칙, 코딩 컨벤션, DB migration 규칙을 검색한다.",
            sourceType = ContextSourceType.MCP_SERVER,
            sourceUri = "https://mcp.internal/dev-docs",
        )

        given(projectRepository.findByIdAndDeletedAtIsNull(10)).willReturn(Optional.of(project))
        given(tenantUserRepository.existsByTenant_IdAndUser_IdAndDeletedAtIsNull(1, 2)).willReturn(true)
        given(projectContextToolService.getProjectTools(10)).willReturn(listOf(tool))

        val result = service.listSources(principal, projectId = 10)

        assertThat(result).hasSize(1)
        assertThat(result.single().id).isEqualTo(3)
        assertThat(result.single().name).isEqualTo("dev_docs_search_3")
        assertThat(result.single().type).isEqualTo(ContextSourceType.MCP_SERVER)
        assertThat(result.single().description).contains("API 개발 규칙")
    }

    @Test
    fun `tenant member can search project context sources`() {
        val principal = AuthenticatedUser(id = 2, email = "dev@example.com", role = UserRole.USER)
        val project = Project(
            id = 10,
            tenant = Tenant(id = 1, name = "dev-team"),
            name = "backend-api",
        )
        val tool = AiToolDefinition(
            sourceId = 3,
            name = "dev_docs_search_3",
            description = "개발 문서를 검색한다.",
            sourceType = ContextSourceType.MCP_SERVER,
            sourceUri = "https://mcp.internal/dev-docs",
        )
        val otherTool = AiToolDefinition(
            sourceId = 4,
            name = "hr_policy_search_4",
            description = "인사 문서를 검색한다.",
            sourceType = ContextSourceType.MCP_SERVER,
            sourceUri = "https://mcp.internal/hr-docs",
        )

        given(projectRepository.findByIdAndDeletedAtIsNull(10)).willReturn(Optional.of(project))
        given(tenantUserRepository.existsByTenant_IdAndUser_IdAndDeletedAtIsNull(1, 2)).willReturn(true)
        given(projectContextToolService.getProjectTools(10)).willReturn(listOf(tool, otherTool))
        given(aiToolExecutor.executeQuery(tool, "API 응답 규칙")).willReturn(
            """
            {
              "documents": [
                {
                  "title": "API Response Convention",
                  "content": "모든 API 응답은 BaseOutput을 상속하고 result 필드에 데이터를 담는다.",
                  "source": "engineering-handbook",
                  "score": 3
                }
              ]
            }
            """.trimIndent(),
        )

        val result = service.search(
            principal,
            ContextSearchRequest(
                projectId = 10,
                query = " API 응답 규칙 ",
                contextSourceIds = listOf(3),
            ),
        )

        assertThat(result).hasSize(1)
        assertThat(result.single().title).isEqualTo("API Response Convention")
        assertThat(result.single().source).isEqualTo("engineering-handbook")
        assertThat(result.single().contextSourceId).isEqualTo(3)
        assertThat(result.single().contextSourceName).isEqualTo("dev_docs_search_3")
        val invocation = contextSearchLoggingService.recordSuccessInvocation()
        assertThat(invocation.arguments[0]).isEqualTo(principal)
        assertThat(invocation.arguments[1]).isEqualTo(project)
        assertThat(invocation.arguments[2]).isEqualTo(
            ContextSearchRequest(
                projectId = 10,
                query = " API 응답 규칙 ",
                contextSourceIds = listOf(3),
            ),
        )
        assertThat(invocation.arguments[3]).isEqualTo(listOf(3L))
        assertThat(invocation.arguments[4]).isEqualTo(1)
        assertThat(invocation.arguments[5] as Long).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `search rejects context source that is not linked to project`() {
        val principal = AuthenticatedUser(id = 2, email = "dev@example.com", role = UserRole.USER)
        val project = Project(
            id = 10,
            tenant = Tenant(id = 1, name = "dev-team"),
            name = "backend-api",
        )
        val tool = AiToolDefinition(
            sourceId = 3,
            name = "dev_docs_search_3",
            description = "개발 문서를 검색한다.",
            sourceType = ContextSourceType.MCP_SERVER,
            sourceUri = "https://mcp.internal/dev-docs",
        )

        given(projectRepository.findByIdAndDeletedAtIsNull(10)).willReturn(Optional.of(project))
        given(tenantUserRepository.existsByTenant_IdAndUser_IdAndDeletedAtIsNull(1, 2)).willReturn(true)
        given(projectContextToolService.getProjectTools(10)).willReturn(listOf(tool))

        val exception = assertThrows<KairosException> {
            service.search(
                principal,
                ContextSearchRequest(
                    projectId = 10,
                    query = "API 응답 규칙",
                    contextSourceIds = listOf(999),
                ),
            )
        }

        assertThat(exception.errorCode).isEqualTo(KairosErrorCode.AI_TOOL_NOT_ALLOWED)
        val invocation = contextSearchLoggingService.recordFailureInvocation()
        assertThat(invocation.arguments[0]).isEqualTo(principal)
        assertThat(invocation.arguments[1]).isEqualTo(project)
        assertThat(invocation.arguments[2]).isEqualTo(
            ContextSearchRequest(
                projectId = 10,
                query = "API 응답 규칙",
                contextSourceIds = listOf(999),
            ),
        )
        assertThat(invocation.arguments[3]).isEqualTo(emptyList<Long>())
        assertThat(invocation.arguments[4] as Long).isGreaterThanOrEqualTo(0)
        assertThat(invocation.arguments[5]).isEqualTo(KairosErrorCode.AI_TOOL_NOT_ALLOWED.code)
    }

    @Test
    fun `user outside tenant cannot search project context sources`() {
        val principal = AuthenticatedUser(id = 9, email = "outsider@example.com", role = UserRole.USER)
        val project = Project(
            id = 10,
            tenant = Tenant(id = 1, name = "dev-team"),
            name = "backend-api",
        )

        given(projectRepository.findByIdAndDeletedAtIsNull(10)).willReturn(Optional.of(project))
        given(tenantUserRepository.existsByTenant_IdAndUser_IdAndDeletedAtIsNull(1, 9)).willReturn(false)

        val exception = assertThrows<KairosException> {
            service.search(
                principal,
                ContextSearchRequest(
                    projectId = 10,
                    query = "API 응답 규칙",
                ),
            )
        }

        assertThat(exception.errorCode).isEqualTo(KairosErrorCode.PROJECT_ACCESS_DENIED)
    }

    private fun ContextSearchLoggingService.recordSuccessInvocation() =
        mockingDetails(this).invocations.single { it.method.name == "recordSuccess" }

    private fun ContextSearchLoggingService.recordFailureInvocation() =
        mockingDetails(this).invocations.single { it.method.name == "recordFailure" }
}
