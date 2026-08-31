package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType
import org.springframework.stereotype.Component

@Component
// 이메일 주소를 검출한다.
class EmailRule : PiiRule {
    override val type = PiiType.EMAIL

    override fun detect(text: String): List<PiiMatch> =
        PATTERN.findAll(text)
            .map { it.toPiiMatch(type) }
            .toList()

    // 도메인은 남긴다. 사내 계정인지 외부 고객인지 구분해야 담당자가 후속 조치를 할 수 있다.
    override fun mask(value: String): String {
        val separatorIndex = value.indexOf('@')
        if (separatorIndex <= 0) {
            return MASK_CHARACTER.toString().repeat(value.length)
        }
        val localPart = value.substring(0, separatorIndex)
        return localPart.first() + MASK_CHARACTER.toString().repeat(localPart.length - 1) +
            value.substring(separatorIndex)
    }

    private companion object {
        private val PATTERN = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
    }
}
