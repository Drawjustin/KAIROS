package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType
import org.springframework.stereotype.Component

@Component
// 대한민국 여권번호를 검출한다. 구형(M12345678)과 2020년 이후 신형(M123A4567)을 모두 지원한다.
// 종별 기호를 M/S/R/O/D로 제한해 일반 일련번호와의 충돌을 줄였지만
// 그래도 제품 코드와 겹칠 여지가 있어 기본 정책은 ALLOW이며 필요한 project만 켜서 쓴다.
class PassportNumberRule : PiiRule {
    override val type = PiiType.PASSPORT_NUMBER

    override fun detect(text: String): List<PiiMatch> =
        PATTERN.findAll(text)
            .map { it.toPiiMatch(type) }
            .toList()

    override fun mask(value: String): String =
        value.first() + MASK_CHARACTER.toString().repeat(value.length - 1)

    private companion object {
        private val PATTERN = Regex(
            """(?<![A-Za-z0-9])[MSRODmsrod](?:\d{8}|\d{3}[A-Za-z]\d{4})(?![A-Za-z0-9])""",
        )
    }
}
