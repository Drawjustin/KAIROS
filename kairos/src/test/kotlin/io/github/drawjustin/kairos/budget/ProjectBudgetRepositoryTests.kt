package io.github.drawjustin.kairos.budget

import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.budget.entity.ProjectBudget
import io.github.drawjustin.kairos.budget.repository.ProjectBudgetRepository
import io.github.drawjustin.kairos.budget.type.BudgetPeriod
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.entity.Tenant
import io.github.drawjustin.kairos.tenant.repository.TenantRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
// 한도 판정이 UPDATE 문 안에서 이루어지는지, 영향받은 행 수로 결과를 알 수 있는지 검증한다.
class ProjectBudgetRepositoryTests : IntegrationTestSupport() {
    @Autowired
    lateinit var tenantRepository: TenantRepository

    @Autowired
    lateinit var projectRepository: ProjectRepository

    @Autowired
    lateinit var projectBudgetRepository: ProjectBudgetRepository

    private val periodStart: Instant = Instant.parse("2026-08-31T00:00:00Z")

    private fun newProject(): Project {
        val tenant = tenantRepository.save(Tenant(name = "tenant-${UUID.randomUUID()}"))
        return projectRepository.save(Project(tenant = tenant, name = "project-${UUID.randomUUID()}"))
    }

    private fun newBudget(
        project: Project,
        requestLimit: Long? = null,
        tokenLimit: Long? = null,
        periodStartedAt: Instant = periodStart,
    ): ProjectBudget =
        projectBudgetRepository.saveAndFlush(
            ProjectBudget(
                project = project,
                period = BudgetPeriod.DAILY,
                requestLimit = requestLimit,
                tokenLimit = tokenLimit,
                periodStartedAt = periodStartedAt,
            ),
        )

    private fun reload(project: Project): ProjectBudget =
        projectBudgetRepository.findByProject_IdAndPeriodAndDeletedAtIsNull(project.id!!, BudgetPeriod.DAILY)!!

    @Test
    fun `reserves a request while the limit still has room`() {
        val project = newProject()
        newBudget(project, requestLimit = 2)

        assertThat(projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)).isEqualTo(1)
        assertThat(projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)).isEqualTo(1)
        assertThat(reload(project).consumedRequests).isEqualTo(2)
    }

    @Test
    fun `refuses the reservation once the request limit is reached`() {
        val project = newProject()
        newBudget(project, requestLimit = 1)

        assertThat(projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)).isEqualTo(1)

        // 영향받은 행이 0이라는 것 자체가 한도 초과 판정이다.
        assertThat(projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)).isEqualTo(0)
        assertThat(reload(project).consumedRequests).isEqualTo(1)
    }

    @Test
    fun `treats a null limit as unlimited`() {
        val project = newProject()
        newBudget(project, requestLimit = null, tokenLimit = null)

        repeat(5) {
            assertThat(projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)).isEqualTo(1)
        }
        assertThat(reload(project).consumedRequests).isEqualTo(5)
    }

    @Test
    fun `refuses the reservation once tokens are already exhausted`() {
        val project = newProject()
        newBudget(project, tokenLimit = 100)

        assertThat(projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)).isEqualTo(1)
        projectBudgetRepository.settleTokens(project.id!!, BudgetPeriod.DAILY, 120)

        // 토큰은 사후 정산이라 한 요청만큼은 넘어설 수 있고, 그다음 요청부터 막힌다.
        assertThat(reload(project).consumedTokens).isEqualTo(120)
        assertThat(projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)).isEqualTo(0)
    }

    @Test
    fun `settles the tokens that the response actually used`() {
        val project = newProject()
        newBudget(project, tokenLimit = 1_000)

        projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)
        projectBudgetRepository.settleTokens(project.id!!, BudgetPeriod.DAILY, 22)
        projectBudgetRepository.settleTokens(project.id!!, BudgetPeriod.DAILY, 30)

        assertThat(reload(project).consumedTokens).isEqualTo(52)
    }

    @Test
    fun `releases a reservation and never goes below zero`() {
        val project = newProject()
        newBudget(project, requestLimit = 5)

        projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)
        projectBudgetRepository.releaseRequest(project.id!!, BudgetPeriod.DAILY)
        assertThat(reload(project).consumedRequests).isEqualTo(0)

        // 보상이 중복 실행되어도 음수로 내려가면 이후 판정이 전부 어긋난다.
        projectBudgetRepository.releaseRequest(project.id!!, BudgetPeriod.DAILY)
        assertThat(reload(project).consumedRequests).isEqualTo(0)
    }

    @Test
    fun `resets the counters when the stored period is older than the current one`() {
        val project = newProject()
        newBudget(project, requestLimit = 5, periodStartedAt = periodStart.minus(1, ChronoUnit.DAYS))
        projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)
        projectBudgetRepository.settleTokens(project.id!!, BudgetPeriod.DAILY, 40)

        assertThat(projectBudgetRepository.resetExpiredPeriod(project.id!!, BudgetPeriod.DAILY, periodStart))
            .isEqualTo(1)

        val budget = reload(project)
        assertThat(budget.consumedRequests).isEqualTo(0)
        assertThat(budget.consumedTokens).isEqualTo(0)
        assertThat(budget.periodStartedAt).isEqualTo(periodStart)
    }

    @Test
    fun `leaves the counters alone when the period has not rolled over`() {
        val project = newProject()
        newBudget(project, requestLimit = 5)
        projectBudgetRepository.reserveRequest(project.id!!, BudgetPeriod.DAILY)

        // 같은 기간 안에서 여러 요청이 동시에 초기화를 시도해도 아무 행도 바뀌지 않아야 한다.
        assertThat(projectBudgetRepository.resetExpiredPeriod(project.id!!, BudgetPeriod.DAILY, periodStart))
            .isEqualTo(0)
        assertThat(reload(project).consumedRequests).isEqualTo(1)
    }
}
