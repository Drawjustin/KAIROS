package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType
import org.springframework.stereotype.Component

@Component
// 내국인 주민등록번호(성별코드 1~4)를 검출한다.
class ResidentRegistrationNumberRule : PiiRule {
    override val type = PiiType.RESIDENT_REGISTRATION_NUMBER

    override fun detect(text: String): List<PiiMatch> =
        RegistrationNumberSupport.PATTERN
            .findValidated(text) { it.isResidentRegistrationNumber() }
            .map { it.toPiiMatch(type) }

    override fun mask(value: String): String = RegistrationNumberSupport.mask(value)

    private fun MatchResult.isResidentRegistrationNumber(): Boolean {
        if (RegistrationNumberSupport.genderCode(this) !in GENDER_CODES) {
            return false
        }
        if (!RegistrationNumberSupport.hasValidBirthDate(this)) {
            return false
        }
        // 구분자를 쓴 값은 사람이 주민번호 형식으로 명시한 것이므로 형식만 맞으면 검출한다.
        // 구분자 없는 13자리는 주문번호 같은 긴 숫자일 수 있어 검증번호까지 통과해야 인정한다.
        return RegistrationNumberSupport.hasSeparator(this) ||
            RegistrationNumberSupport.hasValidChecksum(this)
    }

    private companion object {
        private val GENDER_CODES = setOf('1', '2', '3', '4')
    }
}
