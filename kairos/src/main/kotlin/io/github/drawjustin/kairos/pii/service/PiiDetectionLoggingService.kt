package io.github.drawjustin.kairos.pii.service

import io.github.drawjustin.kairos.pii.entity.PiiDetectionLog
import io.github.drawjustin.kairos.pii.repository.PiiDetectionLogRepository
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiInspectionSource
import io.github.drawjustin.kairos.pii.type.PiiType
import io.github.drawjustin.kairos.observability.KairosMetrics
import io.github.drawjustin.kairos.project.entity.Project
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
// 차단으로 요청이 실패하더라도 검출 사실은 남아야 하므로 별도 트랜잭션으로 저장한다.
class PiiDetectionLoggingService(
    private val piiDetectionLogRepository: PiiDetectionLogRepository,
    private val kairosMetrics: KairosMetrics,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        project: Project,
        source: PiiInspectionSource,
        detectedCounts: Map<PiiType, Int>,
        actions: Map<PiiType, PiiAction>,
    ) {
        if (detectedCounts.isEmpty()) {
            return
        }
        val traceId = MDC.get("traceId")
        piiDetectionLogRepository.saveAll(
            detectedCounts.map { (piiType, detectedCount) ->
                PiiDetectionLog(
                    project = project,
                    source = source,
                    piiType = piiType,
                    detectedCount = detectedCount,
                    action = actions[piiType] ?: piiType.defaultAction,
                    traceId = traceId,
                )
            },
        )
        detectedCounts.forEach { (piiType, detectedCount) ->
            kairosMetrics.recordPiiDetection(
                piiType = piiType,
                action = actions[piiType] ?: piiType.defaultAction,
                detectedCount = detectedCount,
            )
        }
    }
}
