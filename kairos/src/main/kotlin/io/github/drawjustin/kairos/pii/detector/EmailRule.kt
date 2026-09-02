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
    // 다만 로컬 파트가 한 글자면 첫 글자를 남기는 것이 곧 전체를 남기는 것이므로 통째로 가린다.
    override fun mask(value: String): String {
        val separatorIndex = value.indexOf('@')
        if (separatorIndex <= 0) {
            return MASK_CHARACTER.toString().repeat(value.length)
        }
        val localPart = value.substring(0, separatorIndex)
        val maskedLocalPart = if (localPart.length == 1) {
            MASK_CHARACTER.toString()
        } else {
            localPart.first() + MASK_CHARACTER.toString().repeat(localPart.length - 1)
        }
        return maskedLocalPart + value.substring(separatorIndex)
    }

    private companion object {
        // 앞쪽 경계 검사가 없으면 정규식 엔진이 모든 위치에서 다시 시도한다.
        // 숫자나 문자가 길게 이어진 입력에서는 그 재시도가 입력 길이의 제곱으로 늘어나
        // 프롬프트 하나로 CPU를 오래 붙잡아 둘 수 있다.
        // 나머지 규칙이 모두 갖고 있는 경계 검사를 붙이고, 각 구간에 상한을 두어 되돌아가는 폭도 묶는다.
        private val PATTERN = Regex(
            """(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]{1,64}+@[A-Za-z0-9-]{1,63}(?:\.[A-Za-z0-9-]{1,63}){0,8}\.[A-Za-z]{2,24}""",
        )
    }
}
