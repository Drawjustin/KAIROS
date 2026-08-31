package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType
import org.springframework.stereotype.Component

@Component
// 계좌번호를 검출한다. 은행마다 자릿수 규칙이 달라 체크섬으로 검증할 수 없으므로
// 하이픈으로 세 덩이로 끊어 쓴 표기만 잡고, 형식이 겹치는 전화번호와 사업자등록번호를 제외한다.
// 체크섬 검증이 불가능해 오탐 여지가 남는 타입이라 기본 정책을 BLOCK이 아닌 MASK로 둔다.
class BankAccountNumberRule : PiiRule {
    override val type = PiiType.BANK_ACCOUNT_NUMBER

    override fun detect(text: String): List<PiiMatch> =
        PATTERN
            .findValidated(text) { it.value.isBankAccountNumber() }
            .map { it.toPiiMatch(type) }

    // 입금 확인 문의에서 끝자리 대조가 잦아 뒤 3자리만 남긴다.
    override fun mask(value: String): String = maskDigits(value, keepHead = 0, keepTail = 3)

    private fun String.isBankAccountNumber(): Boolean {
        if (BUSINESS_REGISTRATION_PATTERN.matches(this)) {
            return false
        }
        if (LANDLINE_PATTERN.matches(this)) {
            return false
        }
        return count(Char::isDigit) in VALID_LENGTHS
    }

    private companion object {
        private val PATTERN = Regex("""(?<![\d-])\d{2,6}-\d{2,6}-\d{2,6}(?![\d-])""")

        // 사업자등록번호 123-45-67890
        private val BUSINESS_REGISTRATION_PATTERN = Regex("""\d{3}-\d{2}-\d{5}""")

        // 유선전화 02-1234-5678, 031-123-4567
        private val LANDLINE_PATTERN = Regex("""0\d{1,2}-\d{3,4}-\d{4}""")

        private val VALID_LENGTHS = 10..14
    }
}
