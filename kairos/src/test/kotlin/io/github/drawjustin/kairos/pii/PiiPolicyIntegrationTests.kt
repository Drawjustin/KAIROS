package io.github.drawjustin.kairos.pii

import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.pii.entity.ProjectPiiPolicy
import io.github.drawjustin.kairos.pii.repository.PiiDetectionLogRepository
import io.github.drawjustin.kairos.pii.repository.ProjectPiiPolicyRepository
import io.github.drawjustin.kairos.pii.service.PiiGuard
import io.github.drawjustin.kairos.pii.service.PiiPolicyService
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiInspectionSource
import io.github.drawjustin.kairos.pii.type.PiiType
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.entity.Tenant
import io.github.drawjustin.kairos.tenant.repository.TenantRepository
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class PiiPolicyIntegrationTests : IntegrationTestSupport() {
    @Autowired
    lateinit var tenantRepository: TenantRepository

    @Autowired
    lateinit var projectRepository: ProjectRepository

    @Autowired
    lateinit var projectPiiPolicyRepository: ProjectPiiPolicyRepository

    @Autowired
    lateinit var piiDetectionLogRepository: PiiDetectionLogRepository

    @Autowired
    lateinit var piiPolicyService: PiiPolicyService

    @Autowired
    lateinit var piiGuard: PiiGuard

    private fun newProject(): Project {
        val tenant = tenantRepository.save(Tenant(name = "tenant-${UUID.randomUUID()}"))
        return projectRepository.save(Project(tenant = tenant, name = "project-${UUID.randomUUID()}"))
    }

    @Test
    fun `a project with no stored policy falls back to the built-in defaults`() {
        val project = newProject()

        val actions = piiPolicyService.resolveActions(project.id!!)

        assertThat(actions).containsAllEntriesOf(PiiType.entries.associateWith { it.defaultAction })
    }

    @Test
    fun `a stored policy overrides the default for that type only`() {
        val project = newProject()
        projectPiiPolicyRepository.save(
            ProjectPiiPolicy(
                project = project,
                piiType = PiiType.RESIDENT_REGISTRATION_NUMBER,
                action = PiiAction.MASK,
            ),
        )

        val actions = piiPolicyService.resolveActions(project.id!!)

        assertThat(actions[PiiType.RESIDENT_REGISTRATION_NUMBER]).isEqualTo(PiiAction.MASK)
        assertThat(actions[PiiType.CREDIT_CARD_NUMBER]).isEqualTo(PiiAction.BLOCK)
        assertThat(actions[PiiType.PHONE_NUMBER]).isEqualTo(PiiAction.MASK)
    }

    @Test
    fun `a blocked request still writes an audit row that holds no raw value`() {
        val project = newProject()

        val exception = assertThrows<KairosException> {
            piiGuard.inspect(
                project,
                PiiInspectionSource.CHAT_PROMPT,
                "고객 주민번호 900101-1234567 로 조회해줘",
            )
        }
        assertThat(exception.errorCode).isEqualTo(KairosErrorCode.AI_SENSITIVE_DATA_BLOCKED)

        val logs = piiDetectionLogRepository.findAllByProject_IdOrderByIdAsc(project.id!!)
        assertThat(logs).hasSize(1)
        val log = logs.single()
        assertThat(log.piiType).isEqualTo(PiiType.RESIDENT_REGISTRATION_NUMBER)
        assertThat(log.detectedCount).isEqualTo(1)
        assertThat(log.action).isEqualTo(PiiAction.BLOCK)
        assertThat(log.source).isEqualTo(PiiInspectionSource.CHAT_PROMPT)
        assertThat(log.createdAt).isNotNull()
    }

    @Test
    fun `a masked request records the detection and returns the masked text`() {
        val project = newProject()

        val result = piiGuard.inspect(
            project,
            PiiInspectionSource.CONTEXT_QUERY,
            "담당자 연락처 010-1234-5678 알려줘",
        )

        assertThat(result).isEqualTo("담당자 연락처 010-****-5678 알려줘")
        val logs = piiDetectionLogRepository.findAllByProject_IdOrderByIdAsc(project.id!!)
        assertThat(logs).hasSize(1)
        assertThat(logs.single().action).isEqualTo(PiiAction.MASK)
        assertThat(logs.single().source).isEqualTo(PiiInspectionSource.CONTEXT_QUERY)
    }
}
