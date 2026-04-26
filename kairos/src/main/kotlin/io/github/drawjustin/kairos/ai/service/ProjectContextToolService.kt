package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.ai.tool.AiToolDefinition
import io.github.drawjustin.kairos.context.entity.ContextSource
import io.github.drawjustin.kairos.context.repository.ProjectContextSourceRepository
import io.github.drawjustin.kairos.context.type.ContextSourceStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
// project에 허용된 context source를 AI가 호출할 수 있는 내부 공통 tool 목록으로 바꾼다.
class ProjectContextToolService(
    private val projectContextSourceRepository: ProjectContextSourceRepository,
) {
    @Transactional(readOnly = true)
    fun getProjectTools(projectId: Long): List<AiToolDefinition> =
        projectContextSourceRepository.findAllByProject_IdAndDeletedAtIsNullOrderByCreatedAtAsc(projectId)
            .map { it.contextSource }
            .filter { it.status == ContextSourceStatus.ACTIVE && !it.uri.isNullOrBlank() }
            .map { it.toToolDefinition() }

    private fun ContextSource.toToolDefinition(): AiToolDefinition {
        val sourceId = requireNotNull(id) { "ContextSource id must exist" }
        return AiToolDefinition(
            sourceId = sourceId,
            name = name.toToolName(sourceId),
            description = description?.takeIf { it.isNotBlank() }
                ?: "${name} context가 필요할 때 사용한다.",
            sourceType = type,
            sourceUri = requireNotNull(uri) { "ContextSource uri must exist" },
        )
    }

    private fun String.toToolName(sourceId: Long): String {
        val normalized = lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '-')
            .ifBlank { "context_source" }
            .take(MAX_TOOL_NAME_PREFIX_LENGTH)
            .trim('_', '-')
            .ifBlank { "context_source" }

        return "${normalized}_$sourceId"
    }

    private companion object {
        private const val MAX_TOOL_NAME_PREFIX_LENGTH = 55
    }
}
