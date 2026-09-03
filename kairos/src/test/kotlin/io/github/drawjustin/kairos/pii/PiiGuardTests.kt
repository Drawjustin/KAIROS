package io.github.drawjustin.kairos.pii

import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.pii.detector.BankAccountNumberRule
import io.github.drawjustin.kairos.pii.detector.CreditCardNumberRule
import io.github.drawjustin.kairos.pii.detector.EmailRule
import io.github.drawjustin.kairos.pii.detector.ForeignerRegistrationNumberRule
import io.github.drawjustin.kairos.pii.detector.PassportNumberRule
import io.github.drawjustin.kairos.pii.detector.PhoneNumberRule
import io.github.drawjustin.kairos.pii.detector.PiiMasker
import io.github.drawjustin.kairos.pii.detector.PiiScanner
import io.github.drawjustin.kairos.pii.detector.ResidentRegistrationNumberRule
import io.github.drawjustin.kairos.pii.repository.PiiDetectionLogRepository
import io.github.drawjustin.kairos.observability.KairosMetrics
import io.github.drawjustin.kairos.pii.service.PiiDetectionLoggingService
import io.github.drawjustin.kairos.pii.service.PiiGuard
import io.github.drawjustin.kairos.pii.service.PiiPolicyService
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiInspectionSource
import io.github.drawjustin.kairos.pii.type.PiiType
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.tenant.entity.Tenant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class PiiGuardTests {
    private val rules = listOf(
        ResidentRegistrationNumberRule(),
        ForeignerRegistrationNumberRule(),
        CreditCardNumberRule(),
        BankAccountNumberRule(),
        PhoneNumberRule(),
        EmailRule(),
        PassportNumberRule(),
    )
    private val piiPolicyService = mock(PiiPolicyService::class.java)
    private val detectionLogging = RecordingDetectionLoggingService()
    private val guard = PiiGuard(
        piiScanner = PiiScanner(rules),
        piiMasker = PiiMasker(rules),
        piiPolicyService = piiPolicyService,
        piiDetectionLoggingService = detectionLogging,
    )
    private val project = Project(id = PROJECT_ID, tenant = Tenant(id = 1, name = "platform"), name = "KAIROS")

    private fun givenPolicy(vararg overrides: Pair<PiiType, PiiAction>) {
        val actions = PiiType.entries.associateWith { it.defaultAction } + overrides
        given(piiPolicyService.resolveActions(PROJECT_ID)).willReturn(actions)
    }

    @Test
    fun `passes text through untouched and skips policy lookup when nothing is detected`() {
        val text = "다음 분기 리테일본부 매출 목표를 정리해줘"

        val result = guard.inspect(project, PiiInspectionSource.CHAT_PROMPT, text)

        assertThat(result).isEqualTo(text)
        verify(piiPolicyService, never()).resolveActions(PROJECT_ID)
        assertThat(detectionLogging.records).isEmpty()
    }

    @Test
    fun `masks values whose policy is MASK`() {
        givenPolicy()

        val result = guard.inspect(
            project,
            PiiInspectionSource.CHAT_PROMPT,
            "고객 연락처는 010-1234-5678 이고 이메일은 hong@example.com 입니다",
        )

        assertThat(result).isEqualTo("고객 연락처는 010-****-5678 이고 이메일은 h***@example.com 입니다")
    }

    @Test
    fun `blocks the request when a BLOCK type is detected`() {
        givenPolicy()

        val exception = assertThrows<KairosException> {
            guard.inspect(project, PiiInspectionSource.CHAT_PROMPT, "주민번호 900101-1234567 조회해줘")
        }

        assertThat(exception.errorCode).isEqualTo(KairosErrorCode.AI_SENSITIVE_DATA_BLOCKED)
        assertThat(exception.message).contains("주민등록번호")
    }

    @Test
    fun `never puts the detected value into the block message`() {
        givenPolicy()

        val exception = assertThrows<KairosException> {
            guard.inspect(project, PiiInspectionSource.CHAT_PROMPT, "주민번호 900101-1234567 조회해줘")
        }

        assertThat(exception.message).doesNotContain("900101")
        assertThat(exception.message).doesNotContain("1234567")
    }

    @Test
    fun `records the detection before throwing so a blocked request still leaves an audit trail`() {
        givenPolicy()

        assertThrows<KairosException> {
            guard.inspect(project, PiiInspectionSource.CHAT_PROMPT, "주민번호 900101-1234567")
        }

        assertThat(detectionLogging.records).hasSize(1)
        val record = detectionLogging.records.single()
        assertThat(record.source).isEqualTo(PiiInspectionSource.CHAT_PROMPT)
        assertThat(record.detectedCounts).containsExactly(
            java.util.Map.entry(PiiType.RESIDENT_REGISTRATION_NUMBER, 1),
        )
        assertThat(record.actions[PiiType.RESIDENT_REGISTRATION_NUMBER]).isEqualTo(PiiAction.BLOCK)
    }

    @Test
    fun `lets a project relax the default policy`() {
        givenPolicy(PiiType.RESIDENT_REGISTRATION_NUMBER to PiiAction.MASK)

        val result = guard.inspect(project, PiiInspectionSource.CHAT_PROMPT, "주민번호 900101-1234567 조회해줘")

        assertThat(result).isEqualTo("주민번호 900101-******* 조회해줘")
    }

    @Test
    fun `lets a project tighten the default policy`() {
        givenPolicy(PiiType.PHONE_NUMBER to PiiAction.BLOCK)

        assertThrows<KairosException> {
            guard.inspect(project, PiiInspectionSource.CHAT_PROMPT, "연락처 010-1234-5678")
        }
    }

    @Test
    fun `leaves values alone when the policy is ALLOW`() {
        givenPolicy(PiiType.PHONE_NUMBER to PiiAction.ALLOW)

        val result = guard.inspect(project, PiiInspectionSource.CHAT_PROMPT, "연락처 010-1234-5678")

        assertThat(result).isEqualTo("연락처 010-1234-5678")
    }

    @Test
    fun `writes one audit record per request even when several messages carry data`() {
        givenPolicy()

        val result = guard.inspectAll(
            project,
            PiiInspectionSource.CHAT_PROMPT,
            listOf("연락처 010-1234-5678", "메일 hong@example.com", "특이사항 없음"),
        )

        assertThat(result).containsExactly("연락처 010-****-5678", "메일 h***@example.com", "특이사항 없음")
        verify(piiPolicyService, times(1)).resolveActions(PROJECT_ID)
        assertThat(detectionLogging.records).hasSize(1)
        assertThat(detectionLogging.records.single().detectedCounts)
            .containsOnlyKeys(PiiType.PHONE_NUMBER, PiiType.EMAIL)
    }

    private data class LoggedDetection(
        val source: PiiInspectionSource,
        val detectedCounts: Map<PiiType, Int>,
        val actions: Map<PiiType, PiiAction>,
    )

    // Mockito ArgumentCaptor는 Kotlin의 non-null 파라미터와 잘 맞지 않아 직접 기록하는 대역을 쓴다.
    private class RecordingDetectionLoggingService :
        PiiDetectionLoggingService(mock(PiiDetectionLogRepository::class.java), KairosMetrics(SimpleMeterRegistry())) {
        val records = mutableListOf<LoggedDetection>()

        override fun record(
            project: Project,
            source: PiiInspectionSource,
            detectedCounts: Map<PiiType, Int>,
            actions: Map<PiiType, PiiAction>,
        ) {
            records += LoggedDetection(source, detectedCounts, actions)
        }
    }

    private companion object {
        private const val PROJECT_ID = 10L
    }
}
