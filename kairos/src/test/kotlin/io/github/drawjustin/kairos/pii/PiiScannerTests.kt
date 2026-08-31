package io.github.drawjustin.kairos.pii

import io.github.drawjustin.kairos.pii.detector.BankAccountNumberRule
import io.github.drawjustin.kairos.pii.detector.CreditCardNumberRule
import io.github.drawjustin.kairos.pii.detector.EmailRule
import io.github.drawjustin.kairos.pii.detector.ForeignerRegistrationNumberRule
import io.github.drawjustin.kairos.pii.detector.PassportNumberRule
import io.github.drawjustin.kairos.pii.detector.PhoneNumberRule
import io.github.drawjustin.kairos.pii.detector.PiiScanner
import io.github.drawjustin.kairos.pii.detector.ResidentRegistrationNumberRule
import io.github.drawjustin.kairos.pii.type.PiiType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PiiScannerTests {
    private val scanner = PiiScanner(
        listOf(
            ResidentRegistrationNumberRule(),
            ForeignerRegistrationNumberRule(),
            CreditCardNumberRule(),
            BankAccountNumberRule(),
            PhoneNumberRule(),
            EmailRule(),
            PassportNumberRule(),
        ),
    )

    private fun typesIn(text: String): List<PiiType> = scanner.scan(text).map { it.type }

    @Test
    fun `detects resident registration number written with a separator`() {
        assertThat(typesIn("고객 주민번호는 900101-1234567 입니다"))
            .containsExactly(PiiType.RESIDENT_REGISTRATION_NUMBER)
    }

    @Test
    fun `detects unseparated resident registration number only when the checksum holds`() {
        assertThat(typesIn("주민번호 9001011234568"))
            .containsExactly(PiiType.RESIDENT_REGISTRATION_NUMBER)

        assertThat(typesIn("주문번호 9001011234567")).isEmpty()
    }

    @Test
    fun `ignores registration numbers with an impossible birth date`() {
        assertThat(typesIn("901301-1234567")).isEmpty()
        assertThat(typesIn("900230-1234567")).isEmpty()
    }

    @Test
    fun `separates foreigner registration numbers by gender code`() {
        assertThat(typesIn("등록번호 900101-5234567"))
            .containsExactly(PiiType.FOREIGNER_REGISTRATION_NUMBER)

        assertThat(typesIn("등록번호 9001015234567")).isEmpty()
    }

    @Test
    fun `detects credit card numbers that pass the luhn check`() {
        assertThat(typesIn("결제카드 4539 1488 0343 6467"))
            .containsExactly(PiiType.CREDIT_CARD_NUMBER)

        assertThat(typesIn("일련번호 4539 1488 0343 6468")).isEmpty()
    }

    @Test
    fun `detects bank account numbers but not business or landline numbers`() {
        assertThat(typesIn("입금계좌 110-234-567890"))
            .containsExactly(PiiType.BANK_ACCOUNT_NUMBER)

        assertThat(typesIn("사업자등록번호 123-45-67890")).isEmpty()
        assertThat(typesIn("대표번호 02-1234-5678")).isEmpty()
    }

    @Test
    fun `detects mobile phone numbers in both local and international form`() {
        assertThat(typesIn("연락처 010-1234-5678")).containsExactly(PiiType.PHONE_NUMBER)
        assertThat(typesIn("연락처 01012345678")).containsExactly(PiiType.PHONE_NUMBER)
        assertThat(typesIn("연락처 +82 10-1234-5678")).containsExactly(PiiType.PHONE_NUMBER)
    }

    @Test
    fun `detects email addresses`() {
        assertThat(typesIn("문의는 hong.gildong@example.co.kr 로 주세요"))
            .containsExactly(PiiType.EMAIL)
    }

    @Test
    fun `detects both old and new passport number formats`() {
        assertThat(typesIn("여권 M12345678")).containsExactly(PiiType.PASSPORT_NUMBER)
        assertThat(typesIn("여권 M123A4567")).containsExactly(PiiType.PASSPORT_NUMBER)
        assertThat(typesIn("부품코드 X12345678")).isEmpty()
    }

    @Test
    fun `still finds a card number when neighbouring digits break the first candidate`() {
        // 앞의 숫자까지 한 덩이로 묶이면 Luhn 검증에 실패한다.
        // 실패한 구간을 통째로 건너뛰면 뒤에 있는 진짜 카드번호를 놓친다.
        assertThat(typesIn("12 4539 1488 0343 6467"))
            .containsExactly(PiiType.CREDIT_CARD_NUMBER)

        assertThat(typesIn("1000 4539 1488 0343 6467"))
            .containsExactly(PiiType.CREDIT_CARD_NUMBER)
    }

    @Test
    fun `returns matches that never overlap each other`() {
        val text = "홍길동 900101-1234567 010-1234-5678 hong@example.com 4539 1488 0343 6467"

        val matches = scanner.scan(text).sortedBy { it.startIndex }

        assertThat(matches).hasSize(4)
        matches.zipWithNext().forEach { (previous, next) ->
            assertThat(next.startIndex).isGreaterThanOrEqualTo(previous.endIndex)
        }
    }
}
