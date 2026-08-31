package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType
import org.springframework.stereotype.Component

@Component
// 카드번호를 검출한다. 패턴은 넓게 잡고 Luhn 체크섬으로 걸러 오탐을 줄인다.
// 4-4-4-4뿐 아니라 Amex의 4-6-5처럼 구분 위치가 다른 표기도 함께 잡기 위한 구성이다.
class CreditCardNumberRule : PiiRule {
    override val type = PiiType.CREDIT_CARD_NUMBER

    override fun detect(text: String): List<PiiMatch> =
        PATTERN
            .findValidated(text) { it.value.isCreditCardNumber() }
            .map { it.toPiiMatch(type) }

    // 카드번호는 앞 6자리 BIN과 뒤 4자리만 노출하는 것이 업계 관행이나,
    // 사내 프롬프트에서는 식별 가능성을 더 낮추기 위해 앞 4자리까지만 남긴다.
    override fun mask(value: String): String = maskDigits(value, keepHead = 4, keepTail = 4)

    private fun String.isCreditCardNumber(): Boolean {
        val digits = filter(Char::isDigit)
        return digits.length in VALID_LENGTHS && digits.passesLuhn()
    }

    private fun String.passesLuhn(): Boolean {
        var sum = 0
        var double = false
        for (index in indices.reversed()) {
            var digit = Character.getNumericValue(this[index])
            if (double) {
                digit *= 2
                if (digit > 9) {
                    digit -= 9
                }
            }
            sum += digit
            double = !double
        }
        return sum % 10 == 0
    }

    private companion object {
        private val PATTERN = Regex("""(?<![\d-])\d[\d -]{11,21}\d(?![\d-])""")
        private val VALID_LENGTHS = 13..19
    }
}
