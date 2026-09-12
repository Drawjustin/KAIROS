package io.github.drawjustin.kairos.pii

import io.github.drawjustin.kairos.pii.detector.BankAccountNumberRule
import io.github.drawjustin.kairos.pii.detector.CreditCardNumberRule
import io.github.drawjustin.kairos.pii.detector.EmailRule
import io.github.drawjustin.kairos.pii.detector.ForeignerRegistrationNumberRule
import io.github.drawjustin.kairos.pii.detector.PassportNumberRule
import io.github.drawjustin.kairos.pii.detector.PhoneNumberRule
import io.github.drawjustin.kairos.pii.detector.PiiMatch
import io.github.drawjustin.kairos.pii.detector.PiiRule
import io.github.drawjustin.kairos.pii.detector.PiiScanner
import io.github.drawjustin.kairos.pii.detector.ResidentRegistrationNumberRule
import io.github.drawjustin.kairos.pii.detector.toPiiMatch
import io.github.drawjustin.kairos.pii.type.PiiType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

// 검사 시간이 "얼마나 걸리는가"가 아니라 "입력이 커질 때 어떤 형태로 늘어나는가"를 본다.
// 절대 시간은 장비에 따라 달라지지만 증가 형태는 달라지지 않으므로, 배수만 단정한다.
class PiiScannerScalingTests {

    // 수정 전 EmailRule의 정규식을 그대로 되살린다. 앞쪽 경계 검사도, 각 구간의 상한도 없다.
    // 고친 구현이 통과하는 것만으로는 부족하고, 틀린 구현이 실패하는지까지 확인해야 이 테스트에 의미가 생긴다.
    private class UnboundedEmailRule : PiiRule {
        override val type = PiiType.EMAIL

        override fun detect(text: String): List<PiiMatch> =
            PATTERN.findAll(text).map { it.toPiiMatch(type) }.toList()

        override fun mask(value: String): String = value

        private companion object {
            private val PATTERN =
                Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
        }
    }

    private val scanner = scannerWith(EmailRule())
    private val unboundedScanner = scannerWith(UnboundedEmailRule())

    @Test
    fun `scan cost grows in proportion to input length`() {
        // 입력이 4배면 시간도 4배 안쪽이어야 한다. 제곱으로 늘면 16배가 되므로 그 중간을 경계로 둔다.
        val baseline = medianMillis(scanner, digits(BASE_LENGTH))
        val scaledUp = medianMillis(scanner, digits(BASE_LENGTH * SCALE_FACTOR))

        assertThat(scaledUp / baseline)
            .describedAs("입력 ${SCALE_FACTOR}배에 대한 검사 시간 배수")
            .isLessThan(LINEAR_GROWTH_LIMIT)
    }

    @Test
    fun `the missing boundary check made cost grow with the square of input length`() {
        // 경계 검사가 빠진 구현에서는 입력이 2배일 때 시간이 4배가 된다.
        // 이 단정이 깨지면 위 테스트가 우연히 통과하고 있었다는 뜻이다.
        val baseline = medianMillis(unboundedScanner, digits(PROBE_LENGTH))
        val doubled = medianMillis(unboundedScanner, digits(PROBE_LENGTH * 2))

        assertThat(doubled / baseline)
            .describedAs("경계 검사가 없을 때 입력 2배에 대한 검사 시간 배수")
            .isGreaterThan(LINEAR_GROWTH_LIMIT / SCALE_FACTOR)
    }

    private fun scannerWith(emailRule: PiiRule) = PiiScanner(
        listOf(
            ResidentRegistrationNumberRule(),
            ForeignerRegistrationNumberRule(),
            CreditCardNumberRule(),
            BankAccountNumberRule(),
            PhoneNumberRule(),
            emailRule,
            PassportNumberRule(),
        ),
    )

    private fun digits(length: Int) = "9".repeat(length)

    // 첫 호출은 JIT 이전이라 크게 튄다. 예열한 뒤 중앙값을 써서 일시적인 지연에 흔들리지 않게 한다.
    private fun medianMillis(scanner: PiiScanner, text: String): Double {
        repeat(WARMUP_RUNS) { scanner.scan(text) }
        return (1..SAMPLE_RUNS)
            .map {
                val startedAt = System.nanoTime()
                scanner.scan(text)
                (System.nanoTime() - startedAt) / 1_000_000.0
            }
            .sorted()[SAMPLE_RUNS / 2]
    }

    private companion object {
        // 메시지 하나가 가질 수 있는 최대 길이(ChatMessageRequest의 상한)를 기준으로 잡는다.
        private const val BASE_LENGTH = 20_000
        private const val PROBE_LENGTH = 2_500
        private const val SCALE_FACTOR = 4
        private const val LINEAR_GROWTH_LIMIT = 8.0
        private const val WARMUP_RUNS = 2
        private const val SAMPLE_RUNS = 9
    }
}
