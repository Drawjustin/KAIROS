package io.github.drawjustin.kairos.pii.detector

import java.time.LocalDate

// 주민등록번호와 외국인등록번호는 형식이 같고 성별코드만 다르므로 검증 로직을 공유한다.
internal object RegistrationNumberSupport {
    // 앞 6자리(생년월일) + 선택적 구분자 + 성별코드 1자리 + 임의번호 6자리.
    val PATTERN = Regex("""(?<!\d)(\d{6})([-\s]?)([1-8]\d{6})(?!\d)""")

    private val CHECKSUM_WEIGHTS = intArrayOf(2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5)

    fun genderCode(result: MatchResult): Char = result.groupValues[3].first()

    fun hasSeparator(result: MatchResult): Boolean = result.groupValues[2].isNotEmpty()

    // 성별코드가 알려주는 세기를 적용해야 윤년까지 정확히 판정할 수 있다.
    fun hasValidBirthDate(result: MatchResult): Boolean {
        val front = result.groupValues[1]
        val century = when (genderCode(result)) {
            '1', '2', '5', '6' -> 1900
            '3', '4', '7', '8' -> 2000
            else -> return false
        }
        return try {
            LocalDate.of(
                century + front.substring(0, 2).toInt(),
                front.substring(2, 4).toInt(),
                front.substring(4, 6).toInt(),
            )
            true
        } catch (exception: RuntimeException) {
            false
        }
    }

    // 2020-10 이후 발급분은 뒷자리가 임의번호라 이 검증식이 성립하지 않는다.
    // 그래서 검증번호는 "구분자 없이 이어 쓴 13자리 숫자"를 걸러내는 보조 신호로만 쓴다.
    fun hasValidChecksum(result: MatchResult): Boolean {
        val digits = result.groupValues[1] + result.groupValues[3]
        val sum = CHECKSUM_WEIGHTS.indices.sumOf { index ->
            Character.getNumericValue(digits[index]) * CHECKSUM_WEIGHTS[index]
        }
        val expected = (11 - sum % 11) % 10
        return expected == Character.getNumericValue(digits[12])
    }

    fun mask(value: String): String {
        val separator = value.getOrNull(6)?.takeIf { !it.isDigit() }?.toString().orEmpty()
        return value.take(6) + separator + MASK_CHARACTER.toString().repeat(7)
    }
}
