package io.github.drawjustin.kairos.observability

import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.type.AiProvider
import io.github.drawjustin.kairos.ai.type.AiUsageStatus
import io.github.drawjustin.kairos.budget.type.BudgetPeriod
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiType
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// 지표가 실제로 기록되는지, 그리고 태그가 운영 중에 늘어나지 않는 값으로만 이루어졌는지 확인한다.
class KairosMetricsTests {
    private val meterRegistry = SimpleMeterRegistry()
    private val metrics = KairosMetrics(meterRegistry)

    @Test
    fun `counts a successful call with its provider and model`() {
        metrics.recordAiRequest(AiModel.GPT_4O_MINI, AiUsageStatus.SUCCESS, Duration.ofMillis(120))

        val counter = meterRegistry.get("kairos.ai.request")
            .tags("provider", "OPENAI", "model", AiModel.GPT_4O_MINI.value, "status", "SUCCESS", "error_code", "none")
            .counter()

        assertThat(counter.count()).isEqualTo(1.0)
    }

    @Test
    fun `separates failures by error code`() {
        metrics.recordAiRequest(AiModel.GPT_4O_MINI, AiUsageStatus.FAILED, Duration.ofMillis(5), "AI_013")
        metrics.recordAiRequest(AiModel.GPT_4O_MINI, AiUsageStatus.FAILED, Duration.ofMillis(5), "AI_014")
        metrics.recordAiRequest(AiModel.GPT_4O_MINI, AiUsageStatus.FAILED, Duration.ofMillis(5), "AI_014")

        // 격리가 작동한 것과 provider가 죽은 것을 한 덩어리로 세면 대시보드에서 구분할 수 없다.
        assertThat(meterRegistry.get("kairos.ai.request").tags("error_code", "AI_013").counter().count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.get("kairos.ai.request").tags("error_code", "AI_014").counter().count())
            .isEqualTo(2.0)
    }

    @Test
    fun `records how long a call took`() {
        metrics.recordAiRequest(AiModel.GPT_4O_MINI, AiUsageStatus.SUCCESS, Duration.ofMillis(250))

        val timer = meterRegistry.get("kairos.ai.request.duration")
            .tags("provider", "OPENAI", "status", "SUCCESS")
            .timer()

        assertThat(timer.count()).isEqualTo(1)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(250.0)
    }

    @Test
    fun `counts which provider took over for which`() {
        metrics.recordFallback(AiProvider.OPENAI, AiProvider.CLAUDE)

        assertThat(
            meterRegistry.get("kairos.ai.fallback")
                .tags("from_provider", "OPENAI", "to_provider", "CLAUDE")
                .counter().count(),
        ).isEqualTo(1.0)
    }

    @Test
    fun `counts detections by type and by what was done about them`() {
        metrics.recordPiiDetection(PiiType.RESIDENT_REGISTRATION_NUMBER, PiiAction.BLOCK, 1)
        metrics.recordPiiDetection(PiiType.PHONE_NUMBER, PiiAction.MASK, 3)

        assertThat(
            meterRegistry.get("kairos.pii.detection")
                .tags("pii_type", "RESIDENT_REGISTRATION_NUMBER", "action", "BLOCK")
                .counter().count(),
        ).isEqualTo(1.0)
        // 한 요청에서 여러 건이 검출될 수 있으므로 건수를 더한다.
        assertThat(
            meterRegistry.get("kairos.pii.detection")
                .tags("pii_type", "PHONE_NUMBER", "action", "MASK")
                .counter().count(),
        ).isEqualTo(3.0)
    }

    @Test
    fun `counts budget rejections by period`() {
        metrics.recordBudgetRejection(BudgetPeriod.DAILY)
        metrics.recordBudgetRejection(BudgetPeriod.DAILY)

        assertThat(meterRegistry.get("kairos.budget.rejection").tags("period", "DAILY").counter().count())
            .isEqualTo(2.0)
    }

    @Test
    fun `never tags a series with a value that grows during operation`() {
        metrics.recordAiRequest(AiModel.GPT_4O_MINI, AiUsageStatus.SUCCESS, Duration.ofMillis(1))
        metrics.recordFallback(AiProvider.OPENAI, AiProvider.GEMINI)
        metrics.recordPiiDetection(PiiType.EMAIL, PiiAction.MASK, 1)
        metrics.recordBudgetRejection(BudgetPeriod.MONTHLY)

        // project와 tenant는 계속 늘어나므로 태그가 되면 시계열 수가 사실상 무제한이 된다.
        val tagKeys = meterRegistry.meters
            .filter { it.id.name.startsWith("kairos.") }
            .flatMap { it.id.tags }
            .map { it.key }
            .toSet()

        assertThat(tagKeys).doesNotContain("project", "project_id", "tenant", "tenant_id", "api_key")
        assertThat(tagKeys).containsExactlyInAnyOrder(
            "provider", "model", "status", "error_code", "from_provider", "to_provider",
            "pii_type", "action", "period",
        )
    }
}
