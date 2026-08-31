package io.github.drawjustin.kairos.pii

import io.github.drawjustin.kairos.pii.detector.BankAccountNumberRule
import io.github.drawjustin.kairos.pii.detector.CreditCardNumberRule
import io.github.drawjustin.kairos.pii.detector.EmailRule
import io.github.drawjustin.kairos.pii.detector.ForeignerRegistrationNumberRule
import io.github.drawjustin.kairos.pii.detector.PassportNumberRule
import io.github.drawjustin.kairos.pii.detector.PhoneNumberRule
import io.github.drawjustin.kairos.pii.detector.PiiMasker
import io.github.drawjustin.kairos.pii.detector.PiiRule
import io.github.drawjustin.kairos.pii.detector.PiiScanner
import io.github.drawjustin.kairos.pii.detector.ResidentRegistrationNumberRule
import io.github.drawjustin.kairos.pii.repository.PiiDetectionLogRepository
import io.github.drawjustin.kairos.pii.repository.ProjectPiiPolicyRepository
import io.github.drawjustin.kairos.pii.service.PiiDetectionLoggingService
import io.github.drawjustin.kairos.pii.service.PiiGuard
import io.github.drawjustin.kairos.pii.service.PiiPolicyService
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiInspectionSource
import io.github.drawjustin.kairos.pii.type.PiiType
import io.github.drawjustin.kairos.project.entity.Project
import org.mockito.Mockito.mock

// 다른 기능의 테스트가 DB 없이도 PiiGuard를 끼워 넣을 수 있게 하는 대역 모음이다.
val piiRules: List<PiiRule> = listOf(
    ResidentRegistrationNumberRule(),
    ForeignerRegistrationNumberRule(),
    CreditCardNumberRule(),
    BankAccountNumberRule(),
    PhoneNumberRule(),
    EmailRule(),
    PassportNumberRule(),
)

fun piiGuardWith(actions: Map<PiiType, PiiAction>): PiiGuard =
    PiiGuard(
        piiScanner = PiiScanner(piiRules),
        piiMasker = PiiMasker(piiRules),
        piiPolicyService = FixedPiiPolicyService(actions),
        piiDetectionLoggingService = NoOpPiiDetectionLoggingService(),
    )

// 민감정보 통제와 무관한 로직을 검증할 때 쓴다. 어떤 값도 가리거나 막지 않는다.
fun passThroughPiiGuard(): PiiGuard =
    piiGuardWith(PiiType.entries.associateWith { PiiAction.ALLOW })

// 정책을 따로 저장하지 않은 project와 같은 상태를 재현한다.
fun defaultPolicyPiiGuard(): PiiGuard =
    piiGuardWith(PiiType.entries.associateWith { it.defaultAction })

private class FixedPiiPolicyService(
    private val actions: Map<PiiType, PiiAction>,
) : PiiPolicyService(mock(ProjectPiiPolicyRepository::class.java)) {
    override fun resolveActions(projectId: Long): Map<PiiType, PiiAction> = actions
}

private class NoOpPiiDetectionLoggingService :
    PiiDetectionLoggingService(mock(PiiDetectionLogRepository::class.java)) {
    override fun record(
        project: Project,
        source: PiiInspectionSource,
        detectedCounts: Map<PiiType, Int>,
        actions: Map<PiiType, PiiAction>,
    ) = Unit
}
