package io.github.drawjustin.kairos.context.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.ai.service.AiToolExecutor
import io.github.drawjustin.kairos.ai.service.ProjectContextToolService
import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.context.dto.ContextSearchRequest
import io.github.drawjustin.kairos.context.dto.ContextSearchResult
import io.github.drawjustin.kairos.context.dto.ContextSourceCatalogItem
import io.github.drawjustin.kairos.platform.dto.ProjectOutput
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.project.type.ProjectStatus
import io.github.drawjustin.kairos.tenant.repository.TenantUserRepository
import io.github.drawjustin.kairos.user.type.UserRole
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
// 개발 AI 도구가 사내 자료를 직접 뒤지지 않고 KAIROS 정책을 거쳐 참조자료를 받게 하는 진입점이다.
class ContextSearchService(
    private val projectRepository: ProjectRepository,
    private val tenantUserRepository: TenantUserRepository,
    private val projectContextToolService: ProjectContextToolService,
    private val aiToolExecutor: AiToolExecutor,
    private val contextSearchLoggingService: ContextSearchLoggingService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun listProjects(principal: AuthenticatedUser): List<ProjectOutput> {
        val projects = if (principal.role == UserRole.ADMIN) {
            projectRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc()
        } else {
            projectRepository.findAccessibleProjectsByUserId(principal.id)
        }
        return projects.map { it.toOutput() }
    }

    @Transactional(readOnly = true)
    fun listSources(principal: AuthenticatedUser, projectId: Long): List<ContextSourceCatalogItem> {
        val project = resolveAccessibleProject(principal, projectId)
        val resolvedProjectId = requireNotNull(project.id) { "Project id must exist" }
        return projectContextToolService.getProjectTools(resolvedProjectId)
            .map { it.toCatalogItem() }
    }

    fun search(principal: AuthenticatedUser, request: ContextSearchRequest): List<ContextSearchResult> {
        val startedAt = System.nanoTime()
        var searchedContextSourceIds = emptyList<Long>()
        val project = resolveAccessibleProject(principal, request.projectId)
        val projectId = requireNotNull(project.id) { "Project id must exist" }
        return try {
            val query = request.query.trim()
            val tools = projectContextToolService.getProjectTools(projectId)
            val searchTools = tools.filterByRequestedSources(request.contextSourceIds)
            searchedContextSourceIds = searchTools.map { it.sourceId }
            val results = searchTools
                .flatMap { tool ->
                    val rawResponse = aiToolExecutor.executeQuery(tool, query)
                    rawResponse.toSearchResults(tool)
                }
                .sortedWith(
                    compareByDescending<ContextSearchResult> { it.score }
                        .thenBy { it.title }
                        .thenBy { it.contextSourceName },
                )

            contextSearchLoggingService.recordSuccess(
                principal = principal,
                project = project,
                request = request,
                searchedContextSourceIds = searchedContextSourceIds,
                resultCount = results.size,
                latencyMs = elapsedMillis(startedAt),
            )
            results
        } catch (exception: KairosException) {
            contextSearchLoggingService.recordFailure(
                principal = principal,
                project = project,
                request = request,
                searchedContextSourceIds = searchedContextSourceIds,
                latencyMs = elapsedMillis(startedAt),
                errorCode = exception.errorCode.code,
            )
            throw exception
        } catch (exception: Exception) {
            contextSearchLoggingService.recordFailure(
                principal = principal,
                project = project,
                request = request,
                searchedContextSourceIds = searchedContextSourceIds,
                latencyMs = elapsedMillis(startedAt),
                errorCode = KairosErrorCode.INTERNAL_SERVER_ERROR.code,
            )
            throw exception
        }
    }

    private fun resolveAccessibleProject(principal: AuthenticatedUser, projectId: Long): Project {
        val project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow { KairosException(KairosErrorCode.PROJECT_NOT_FOUND) }
        if (project.status != ProjectStatus.ACTIVE) {
            throw KairosException(KairosErrorCode.AI_PROJECT_INACTIVE)
        }

        val tenantId = requireNotNull(project.tenant.id) { "Project tenant id must exist" }
        if (principal.role != UserRole.ADMIN &&
            !tenantUserRepository.existsByTenant_IdAndUser_IdAndDeletedAtIsNull(
                tenantId = tenantId,
                userId = principal.id,
            )
        ) {
            throw KairosException(KairosErrorCode.PROJECT_ACCESS_DENIED)
        }
        return project
    }

    private fun List<AiToolDefinition>.filterByRequestedSources(contextSourceIds: List<Long>?): List<AiToolDefinition> {
        val requestedIds = contextSourceIds
            ?.distinct()
            ?: return this

        if (requestedIds.isEmpty()) {
            return this
        }

        val filteredTools = filter { it.sourceId in requestedIds }
        if (filteredTools.size != requestedIds.size) {
            throw KairosException(KairosErrorCode.AI_TOOL_NOT_ALLOWED)
        }
        return filteredTools
    }

    private fun AiToolDefinition.toCatalogItem(): ContextSourceCatalogItem =
        ContextSourceCatalogItem(
            id = sourceId,
            name = name,
            type = sourceType,
            description = description,
        )

    private fun Project.toOutput(): ProjectOutput =
        ProjectOutput(
            id = requireNotNull(id) { "Project id must exist" },
            tenantId = requireNotNull(tenant.id) { "Project tenant id must exist" },
            name = name,
            environment = environment,
            status = status,
            createdAt = requireNotNull(createdAt) { "Project createdAt must exist" },
        )

    private fun elapsedMillis(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private fun String.toSearchResults(tool: AiToolDefinition): List<ContextSearchResult> {
        val root = try {
            objectMapper.readTree(this)
        } catch (exception: Exception) {
            return listOf(toRawSearchResult(tool))
        }

        val documents = root.path("documents")
        if (!documents.isArray) {
            return listOf(toRawSearchResult(tool))
        }

        return documents.mapIndexedNotNull { index, document ->
            document.toSearchResult(tool, index)
        }
    }

    private fun JsonNode.toSearchResult(tool: AiToolDefinition, index: Int): ContextSearchResult? {
        val content = path("content").asText("").trim()
        if (content.isBlank()) {
            return null
        }
        return ContextSearchResult(
            title = path("title").asText("Context result ${index + 1}").trim(),
            content = content,
            source = path("source").asText(tool.name).trim(),
            score = path("score").asInt(0),
            contextSourceId = tool.sourceId,
            contextSourceName = tool.name,
            contextSourceType = tool.sourceType,
        )
    }

    private fun String.toRawSearchResult(tool: AiToolDefinition): ContextSearchResult =
        ContextSearchResult(
            title = tool.name,
            content = trim(),
            source = tool.name,
            score = 0,
            contextSourceId = tool.sourceId,
            contextSourceName = tool.name,
            contextSourceType = tool.sourceType,
        )
}
