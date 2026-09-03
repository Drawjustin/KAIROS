package io.github.drawjustin.kairos.observability

import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.type.AiProvider
import io.github.drawjustin.kairos.ai.type.AiUsageStatus
import io.github.drawjustin.kairos.budget.type.BudgetPeriod
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiType
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import org.springframework.stereotype.Component

@Component
// 통제 계층에서 일어난 일을 운영 지표로 노출한다.
//
// 태그에는 project나 tenant를 넣지 않는다. 둘 다 계속 늘어나는 값이라
// 시계열 수가 사실상 무제한이 되고 Prometheus 메모리가 먼저 무너진다.
// "어느 project가 얼마나 썼는가"는 이미 만들어 둔 사용량 집계 API가 답하고,
// 지표는 "시스템이 지금 건강한가"만 본다. 두 질문의 저장소를 나눈 것이다.
//
// 그래서 여기 쓰이는 태그는 전부 enum이다. 값의 종류가 코드에 고정되어 있어
// 운영 중에 시계열이 늘어날 수 없다.
class KairosMetrics(
    private val meterRegistry: MeterRegistry,
) {
    fun recordAiRequest(
        model: AiModel,
        status: AiUsageStatus,
        latency: Duration,
        errorCode: String? = null,
    ) {
        meterRegistry.counter(
            AI_REQUEST_TOTAL,
            "provider", model.provider.name,
            "model", model.value,
            "status", status.name,
            // 실패 원인을 구분해야 격리 작동과 provider 장애와 요청 오류를 따로 셀 수 있다.
            "error_code", errorCode ?: NONE,
        ).increment()

        Timer.builder(AI_REQUEST_DURATION)
            .tag("provider", model.provider.name)
            .tag("model", model.value)
            .tag("status", status.name)
            .register(meterRegistry)
            .record(latency)
    }

    // 요청한 provider가 아니라 다른 provider가 답한 경우다. 장애 대응이 실제로 작동한 횟수다.
    fun recordFallback(fromProvider: AiProvider, toProvider: AiProvider) {
        meterRegistry.counter(
            AI_FALLBACK_TOTAL,
            "from_provider", fromProvider.name,
            "to_provider", toProvider.name,
        ).increment()
    }

    fun recordPiiDetection(piiType: PiiType, action: PiiAction, detectedCount: Int) {
        meterRegistry.counter(
            PII_DETECTION_TOTAL,
            "pii_type", piiType.name,
            "action", action.name,
        ).increment(detectedCount.toDouble())
    }

    fun recordBudgetRejection(period: BudgetPeriod) {
        meterRegistry.counter(BUDGET_REJECTION_TOTAL, "period", period.name).increment()
    }

    private companion object {
        private const val AI_REQUEST_TOTAL = "kairos.ai.request"
        private const val AI_REQUEST_DURATION = "kairos.ai.request.duration"
        private const val AI_FALLBACK_TOTAL = "kairos.ai.fallback"
        private const val PII_DETECTION_TOTAL = "kairos.pii.detection"
        private const val BUDGET_REJECTION_TOTAL = "kairos.budget.rejection"

        private const val NONE = "none"
    }
}
