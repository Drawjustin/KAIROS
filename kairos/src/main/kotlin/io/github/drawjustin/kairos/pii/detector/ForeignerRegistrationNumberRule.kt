package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType
import org.springframework.stereotype.Component

@Component
// 외국인등록번호(성별코드 5~8)를 검출한다.
// 2020년 이후 발급분은 검증번호 규칙이 사라져 신뢰할 수 없으므로 구분자가 있는 표기만 검출한다.
// 구분자 없이 이어 쓴 13자리는 놓칠 수 있고, 이는 오탐을 막기 위해 감수한 한계다.
class ForeignerRegistrationNumberRule : PiiRule {
    override val type = PiiType.FOREIGNER_REGISTRATION_NUMBER

    override fun detect(text: String): List<PiiMatch> =
        RegistrationNumberSupport.PATTERN
            .findValidated(text) { it.isForeignerRegistrationNumber() }
            .map { it.toPiiMatch(type) }

    override fun mask(value: String): String = RegistrationNumberSupport.mask(value)

    private fun MatchResult.isForeignerRegistrationNumber(): Boolean =
        RegistrationNumberSupport.genderCode(this) in GENDER_CODES &&
            RegistrationNumberSupport.hasSeparator(this) &&
            RegistrationNumberSupport.hasValidBirthDate(this)

    private companion object {
        private val GENDER_CODES = setOf('5', '6', '7', '8')
    }
}
