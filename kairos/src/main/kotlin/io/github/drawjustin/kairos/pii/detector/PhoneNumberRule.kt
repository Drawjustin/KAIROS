package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType
import org.springframework.stereotype.Component

@Component
// 국내 휴대전화번호를 검출한다. 국가번호(+82) 표기도 함께 처리한다.
class PhoneNumberRule : PiiRule {
    override val type = PiiType.PHONE_NUMBER

    override fun detect(text: String): List<PiiMatch> =
        PATTERN.findAll(text)
            .map { it.toPiiMatch(type) }
            .toList()

    // 통신사 식별번호와 끝 4자리는 남겨 상담 이력에서 같은 번호인지 대조할 수 있게 한다.
    override fun mask(value: String): String = maskDigits(value, keepHead = 3, keepTail = 4)

    private companion object {
        private val PATTERN = Regex("""(?<![\d-])(?:\+82[ -]?1|01)[016789][ -]?\d{3,4}[ -]?\d{4}(?![\d-])""")
    }
}
