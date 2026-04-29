package io.github.drawjustin.kairos.context.service

import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.context.dto.ContextSearchRequest
import io.github.drawjustin.kairos.context.entity.ContextSearchLog
import io.github.drawjustin.kairos.context.repository.ContextSearchLogRepository
import io.github.drawjustin.kairos.context.type.ContextSearchPurpose
import io.github.drawjustin.kairos.context.type.ContextSearchStatus
import io.github.drawjustin.kairos.project.entity.Project
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
// Context 검색 감사 로그는 검색 요청 성공/실패와 별개로 남아야 하므로 별도 트랜잭션으로 저장한다.
class ContextSearchLoggingService(
    private val contextSearchLogRepository: ContextSearchLogRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordSuccess(
        principal: AuthenticatedUser,
        project: Project,
        request: ContextSearchRequest,
        searchedContextSourceIds: Collection<Long>,
        resultCount: Int,
        latencyMs: Long,
    ) = recordSuccess(
        userId = principal.id,
        project = project,
        purpose = request.purpose,
        query = request.query,
        requestedContextSourceIds = request.contextSourceIds,
        searchedContextSourceIds = searchedContextSourceIds,
        resultCount = resultCount,
        latencyMs = latencyMs,
    )

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordSuccess(
        userId: Long,
        project: Project,
        purpose: ContextSearchPurpose,
        query: String,
        requestedContextSourceIds: Collection<Long>?,
        searchedContextSourceIds: Collection<Long>,
        resultCount: Int,
        latencyMs: Long,
    ) {
        contextSearchLogRepository.save(
            ContextSearchLog(
                userId = userId,
                project = project,
                purpose = purpose,
                query = query.toLogQuery(),
                requestedContextSourceIds = requestedContextSourceIds.toCsvOrNull(),
                searchedContextSourceIds = searchedContextSourceIds.toCsvOrNull(),
                resultCount = resultCount,
                status = ContextSearchStatus.SUCCESS,
                latencyMs = latencyMs,
                traceId = currentTraceId(),
            ),
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(
        principal: AuthenticatedUser,
        project: Project,
        request: ContextSearchRequest,
        searchedContextSourceIds: Collection<Long>,
        latencyMs: Long,
        errorCode: String,
    ) = recordFailure(
        userId = principal.id,
        project = project,
        purpose = request.purpose,
        query = request.query,
        requestedContextSourceIds = request.contextSourceIds,
        searchedContextSourceIds = searchedContextSourceIds,
        latencyMs = latencyMs,
        errorCode = errorCode,
    )

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(
        userId: Long,
        project: Project,
        purpose: ContextSearchPurpose,
        query: String,
        requestedContextSourceIds: Collection<Long>?,
        searchedContextSourceIds: Collection<Long>,
        latencyMs: Long,
        errorCode: String,
    ) {
        contextSearchLogRepository.save(
            ContextSearchLog(
                userId = userId,
                project = project,
                purpose = purpose,
                query = query.toLogQuery(),
                requestedContextSourceIds = requestedContextSourceIds.toCsvOrNull(),
                searchedContextSourceIds = searchedContextSourceIds.toCsvOrNull(),
                status = ContextSearchStatus.FAILED,
                latencyMs = latencyMs,
                errorCode = errorCode,
                traceId = currentTraceId(),
            ),
        )
    }

    private fun Collection<Long>?.toCsvOrNull(): String? =
        this
            ?.distinct()
            ?.sorted()
            ?.joinToString(",")
            ?.takeIf { it.isNotBlank() }

    private fun currentTraceId(): String? = MDC.get("traceId")

    private fun String.toLogQuery(): String = trim().take(MAX_QUERY_LENGTH)

    private companion object {
        private const val MAX_QUERY_LENGTH = 2_000
    }
}
