package io.github.drawjustin.kairos.budget

import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.budget.entity.ProjectBudget
import io.github.drawjustin.kairos.budget.repository.ProjectBudgetRepository
import io.github.drawjustin.kairos.budget.service.BudgetGuard
import io.github.drawjustin.kairos.budget.type.BudgetPeriod
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.project.entity.Project
import io.github.drawjustin.kairos.project.repository.ProjectRepository
import io.github.drawjustin.kairos.tenant.entity.Tenant
import io.github.drawjustin.kairos.tenant.repository.TenantRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
// 예산 통제의 핵심 주장은 "동시 요청에서도 한도가 정확히 지켜진다"이다.
// 요청을 한 줄로 세워 검증하면 그 주장을 전혀 검증하지 못하므로 실제로 동시에 밀어 넣는다.
class BudgetGuardConcurrencyTests : IntegrationTestSupport() {
    @Autowired
    lateinit var tenantRepository: TenantRepository

    @Autowired
    lateinit var projectRepository: ProjectRepository

    @Autowired
    lateinit var projectBudgetRepository: ProjectBudgetRepository

    @Autowired
    lateinit var budgetGuard: BudgetGuard

    private fun newProjectWithBudget(
        requestLimit: Long? = null,
        tokenLimit: Long? = null,
        period: BudgetPeriod = BudgetPeriod.DAILY,
    ): Project {
        val tenant = tenantRepository.save(Tenant(name = "tenant-${UUID.randomUUID()}"))
        val project = projectRepository.save(Project(tenant = tenant, name = "project-${UUID.randomUUID()}"))
        projectBudgetRepository.saveAndFlush(
            ProjectBudget(
                project = project,
                period = period,
                requestLimit = requestLimit,
                tokenLimit = tokenLimit,
                // 테스트 중 기간이 넘어가 초기화되는 일이 없도록 충분히 이전 시각을 둔다.
                periodStartedAt = Instant.now(),
            ),
        )
        return project
    }

    private data class Outcome(val accepted: Int, val rejected: Int)

    // 모든 스레드를 출발선에 세운 뒤 한 번에 풀어야 경합이 실제로 발생한다.
    private fun reserveConcurrently(projectId: Long, concurrency: Int): Outcome {
        val ready = CountDownLatch(concurrency)
        val start = CountDownLatch(1)
        val accepted = AtomicInteger()
        val rejected = AtomicInteger()

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            repeat(concurrency) {
                executor.submit {
                    ready.countDown()
                    start.await()
                    try {
                        budgetGuard.reserve(projectId)
                        accepted.incrementAndGet()
                    } catch (exception: KairosException) {
                        assertThat(exception.errorCode).isEqualTo(KairosErrorCode.AI_BUDGET_EXCEEDED)
                        rejected.incrementAndGet()
                    }
                }
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue()
            start.countDown()
        }
        return Outcome(accepted.get(), rejected.get())
    }

    @Test
    fun `lets exactly the allowed number of concurrent requests through`() {
        val requestLimit = 100L
        val concurrency = 300
        val project = newProjectWithBudget(requestLimit = requestLimit)

        val outcome = reserveConcurrently(project.id!!, concurrency)

        assertThat(outcome.accepted).isEqualTo(requestLimit.toInt())
        assertThat(outcome.rejected).isEqualTo(concurrency - requestLimit.toInt())

        // 원장도 정확히 한도만큼만 소진되어 있어야 한다.
        val budget = projectBudgetRepository
            .findByProject_IdAndPeriodAndDeletedAtIsNull(project.id!!, BudgetPeriod.DAILY)!!
        assertThat(budget.consumedRequests).isEqualTo(requestLimit)
    }

    @Test
    fun `keeps the ledger consistent when every reservation is compensated`() {
        val project = newProjectWithBudget(requestLimit = 50)
        val concurrency = 50
        val start = CountDownLatch(1)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            repeat(concurrency) {
                executor.submit {
                    start.await()
                    val reservation = budgetGuard.reserve(project.id!!)
                    // provider 호출이 실패한 상황을 재현한다.
                    budgetGuard.release(reservation)
                }
            }
            start.countDown()
        }

        val budget = projectBudgetRepository
            .findByProject_IdAndPeriodAndDeletedAtIsNull(project.id!!, BudgetPeriod.DAILY)!!
        assertThat(budget.consumedRequests).isEqualTo(0)
    }

    @Test
    fun `adds up every settled token without losing an update`() {
        val project = newProjectWithBudget(tokenLimit = 1_000_000)
        val concurrency = 100
        val tokensPerCall = 30
        val start = CountDownLatch(1)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            repeat(concurrency) {
                executor.submit {
                    start.await()
                    val reservation = budgetGuard.reserve(project.id!!)
                    budgetGuard.settle(reservation, tokensPerCall)
                }
            }
            start.countDown()
        }

        val budget = projectBudgetRepository
            .findByProject_IdAndPeriodAndDeletedAtIsNull(project.id!!, BudgetPeriod.DAILY)!!
        // 읽고 더해서 쓰는 방식이었다면 여기서 갱신 손실이 드러난다.
        assertThat(budget.consumedTokens).isEqualTo((concurrency * tokensPerCall).toLong())
    }

    @Test
    fun `rolls back the daily reservation when the monthly limit refuses`() {
        val tenant = tenantRepository.save(Tenant(name = "tenant-${UUID.randomUUID()}"))
        val project = projectRepository.save(Project(tenant = tenant, name = "project-${UUID.randomUUID()}"))
        projectBudgetRepository.saveAndFlush(
            ProjectBudget(
                project = project,
                period = BudgetPeriod.DAILY,
                requestLimit = 10,
                periodStartedAt = Instant.now(),
            ),
        )
        projectBudgetRepository.saveAndFlush(
            ProjectBudget(
                project = project,
                period = BudgetPeriod.MONTHLY,
                requestLimit = 0,
                periodStartedAt = Instant.now(),
            ),
        )

        val exception = runCatching { budgetGuard.reserve(project.id!!) }.exceptionOrNull()

        assertThat(exception).isInstanceOf(KairosException::class.java)
        assertThat((exception as KairosException).errorCode).isEqualTo(KairosErrorCode.AI_BUDGET_EXCEEDED)

        // 월 한도에서 막혔으니 앞서 선점한 일 한도도 되돌아가 있어야 한다.
        val daily = projectBudgetRepository
            .findByProject_IdAndPeriodAndDeletedAtIsNull(project.id!!, BudgetPeriod.DAILY)!!
        assertThat(daily.consumedRequests).isEqualTo(0)
    }

    @Test
    fun `treats a project without a budget as unlimited`() {
        val tenant = tenantRepository.save(Tenant(name = "tenant-${UUID.randomUUID()}"))
        val project = projectRepository.save(Project(tenant = tenant, name = "project-${UUID.randomUUID()}"))

        val reservation = budgetGuard.reserve(project.id!!)

        assertThat(reservation.reserved).isFalse()
        // 통제할 대상이 없으므로 정산과 보상도 아무 일도 하지 않는다.
        budgetGuard.settle(reservation, 100)
        budgetGuard.release(reservation)
    }
}
