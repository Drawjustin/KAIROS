package io.github.drawjustin.kairos.pii

import io.github.drawjustin.kairos.pii.detector.BankAccountNumberRule
import io.github.drawjustin.kairos.pii.detector.CreditCardNumberRule
import io.github.drawjustin.kairos.pii.detector.EmailRule
import io.github.drawjustin.kairos.pii.detector.ForeignerRegistrationNumberRule
import io.github.drawjustin.kairos.pii.detector.PassportNumberRule
import io.github.drawjustin.kairos.pii.detector.PhoneNumberRule
import io.github.drawjustin.kairos.pii.detector.PiiMasker
import io.github.drawjustin.kairos.pii.detector.PiiScanner
import io.github.drawjustin.kairos.pii.detector.ResidentRegistrationNumberRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PiiMaskerTests {
    private val rules = listOf(
        ResidentRegistrationNumberRule(),
        ForeignerRegistrationNumberRule(),
        CreditCardNumberRule(),
        BankAccountNumberRule(),
        PhoneNumberRule(),
        EmailRule(),
        PassportNumberRule(),
    )
    private val scanner = PiiScanner(rules)
    private val masker = PiiMasker(rules)

    private fun mask(text: String): String = masker.mask(text, scanner.scan(text))

    @Test
    fun `keeps the birth date and hides the rest of a registration number`() {
        assertThat(mask("900101-1234567")).isEqualTo("900101-*******")
        assertThat(mask("9001011234568")).isEqualTo("900101*******")
    }

    @Test
    fun `keeps the leading and trailing four digits of a card number`() {
        assertThat(mask("4539 1488 0343 6467")).isEqualTo("4539 **** **** 6467")
    }

    @Test
    fun `keeps the carrier prefix and last four digits of a phone number`() {
        assertThat(mask("010-1234-5678")).isEqualTo("010-****-5678")
    }

    @Test
    fun `keeps the domain of an email address`() {
        assertThat(mask("hong@example.com")).isEqualTo("h***@example.com")
    }

    @Test
    fun `hides a single character local part entirely`() {
        // 첫 글자를 남기는 규칙을 그대로 적용하면 한 글자짜리 주소는 원문 그대로 통과한다.
        assertThat(mask("a@example.com")).isEqualTo("*@example.com")
    }

    @Test
    fun `keeps only the last three digits of a bank account number`() {
        assertThat(mask("110-234-567890")).isEqualTo("***-***-***890")
    }

    @Test
    fun `keeps only the passport type letter`() {
        assertThat(mask("M12345678")).isEqualTo("M********")
    }

    @Test
    fun `masks every detected value in a mixed sentence`() {
        val text = "고객 홍길동 주민번호 900101-1234567, 연락처 010-1234-5678, 이메일 hong@example.com"

        assertThat(mask(text))
            .isEqualTo("고객 홍길동 주민번호 900101-*******, 연락처 010-****-5678, 이메일 h***@example.com")
    }

    @Test
    fun `leaves text without sensitive data untouched`() {
        val text = "다음 분기 매출 목표는 120억이고 담당 부서는 리테일본부입니다"

        assertThat(mask(text)).isEqualTo(text)
    }
}
