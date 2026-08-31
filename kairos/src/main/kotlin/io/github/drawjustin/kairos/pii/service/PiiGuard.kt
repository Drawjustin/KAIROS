package io.github.drawjustin.kairos.pii.service

import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.pii.detector.PiiMasker
import io.github.drawjustin.kairos.pii.detector.PiiMatch
import io.github.drawjustin.kairos.pii.detector.PiiScanner
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiInspectionSource
import io.github.drawjustin.kairos.pii.type.PiiType
import io.github.drawjustin.kairos.project.entity.Project
import org.springframework.stereotype.Service

@Service
// 민감정보 검사와 정책 적용을 한 곳에 모은 진입점이다.
// 호출 경로들은 이 클래스만 알면 되고, 검출 규칙이나 정책 저장 방식은 알 필요가 없다.
class PiiGuard(
    private val piiScanner: PiiScanner,
    private val piiMasker: PiiMasker,
    private val piiPolicyService: PiiPolicyService,
    private val piiDetectionLoggingService: PiiDetectionLoggingService,
) {
    fun inspect(project: Project, source: PiiInspectionSource, text: String): String =
        inspectAll(project, source, listOf(text)).first()

    // 한 요청에 여러 문자열이 있어도 정책 조회와 감사 로그는 한 번만 수행한다.
    // 메시지마다 로그를 남기면 같은 요청이 여러 건으로 흩어져 감사 시 세기 어려워진다.
    fun inspectAll(project: Project, source: PiiInspectionSource, texts: List<String>): List<String> {
        val scanned = texts.map { it to piiScanner.scan(it) }
        val detectedCounts = scanned
            .flatMap { (_, matches) -> matches }
            .groupingBy { it.type }
            .eachCount()
        if (detectedCounts.isEmpty()) {
            return texts
        }

        val projectId = requireNotNull(project.id) { "Project id must exist" }
        val actions = piiPolicyService.resolveActions(projectId)

        // 차단으로 요청이 끊기더라도 검출 사실은 남아야 하므로 예외를 던지기 전에 기록한다.
        piiDetectionLoggingService.record(
            project = project,
            source = source,
            detectedCounts = detectedCounts,
            actions = actions,
        )

        val blockedTypes = detectedCounts.keys
            .filter { actions.actionOf(it) == PiiAction.BLOCK }
            .sortedBy { it.ordinal }
        if (blockedTypes.isNotEmpty()) {
            throw KairosException(
                KairosErrorCode.AI_SENSITIVE_DATA_BLOCKED,
                // 어떤 종류가 걸렸는지는 알려주되 값 자체는 어떤 형태로도 응답에 담지 않는다.
                blockedTypes.joinToString(", ") { it.displayName } + " 이(가) 포함되어 요청을 차단했습니다",
            )
        }

        return scanned.map { (text, matches) ->
            piiMasker.mask(text, matches.filterMaskable(actions))
        }
    }

    private fun List<PiiMatch>.filterMaskable(actions: Map<PiiType, PiiAction>): List<PiiMatch> =
        filter { actions.actionOf(it.type) == PiiAction.MASK }

    private fun Map<PiiType, PiiAction>.actionOf(piiType: PiiType): PiiAction =
        this[piiType] ?: piiType.defaultAction
}
