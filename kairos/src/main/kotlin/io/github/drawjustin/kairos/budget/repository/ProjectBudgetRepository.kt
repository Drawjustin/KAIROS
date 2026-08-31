package io.github.drawjustin.kairos.budget.repository

import io.github.drawjustin.kairos.budget.entity.ProjectBudget
import io.github.drawjustin.kairos.budget.type.BudgetPeriod
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

// 한도 판정을 애플리케이션이 아니라 UPDATE 문 안에서 수행한다.
// 조회 후 비교하고 다시 쓰면 그 사이에 다른 요청이 끼어들어 한도가 뚫린다.
// 조건을 WHERE에 넣으면 판정과 증가가 한 문장 안에서 원자적으로 일어나고,
// 애플리케이션은 영향받은 행 수만 보면 된다.
// 각 UPDATE는 자기 자신만의 짧은 트랜잭션에서 끝나야 한다.
// AI 호출을 감싸는 트랜잭션 안에서 실행되면 응답을 기다리는 수 초 동안 예산 행의 잠금이 유지되고,
// 같은 project의 모든 요청이 줄을 서게 되어 락을 피하려고 만든 구조가 무의미해진다.
interface ProjectBudgetRepository : JpaRepository<ProjectBudget, Long> {
    fun findAllByProject_IdAndDeletedAtIsNullOrderByPeriodAsc(projectId: Long): List<ProjectBudget>

    fun findByProject_IdAndPeriodAndDeletedAtIsNull(projectId: Long, period: BudgetPeriod): ProjectBudget?

    // 기간이 넘어갔으면 소진량을 0으로 되돌린다.
    // 스케줄러로 일괄 초기화하지 않는 이유는 인스턴스가 여러 대일 때 중복 실행 문제가 새로 생기기 때문이다.
    // 자정 직후 동시에 들어온 요청이 모두 이 문장을 실행해도 조건 덕분에 정확히 하나만 성공한다.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update ProjectBudget b
           set b.consumedRequests = 0,
               b.consumedTokens = 0,
               b.periodStartedAt = :periodStartedAt,
               b.updatedAt = current_timestamp
         where b.project.id = :projectId
           and b.period = :period
           and b.deletedAt is null
           and b.periodStartedAt < :periodStartedAt
        """,
    )
    fun resetExpiredPeriod(
        @Param("projectId") projectId: Long,
        @Param("period") period: BudgetPeriod,
        @Param("periodStartedAt") periodStartedAt: Instant,
    ): Int

    // 호출 1건을 선점한다. 영향받은 행이 0이면 한도를 넘은 것이다.
    // 토큰은 호출 전에 알 수 없으므로 "이미 한도에 도달했는가"만 본다.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update ProjectBudget b
           set b.consumedRequests = b.consumedRequests + 1,
               b.updatedAt = current_timestamp
         where b.project.id = :projectId
           and b.period = :period
           and b.deletedAt is null
           and (b.requestLimit is null or b.consumedRequests + 1 <= b.requestLimit)
           and (b.tokenLimit is null or b.consumedTokens < b.tokenLimit)
        """,
    )
    fun reserveRequest(
        @Param("projectId") projectId: Long,
        @Param("period") period: BudgetPeriod,
    ): Int

    // 응답이 온 뒤 실제 토큰으로 정산한다.
    // 여기에는 한도 조건을 걸지 않는다. 이미 발생한 사용량을 기록하지 않으면 원장이 어긋나기 때문이다.
    // 대신 한도를 넘긴 상태가 되면 다음 요청의 reserveRequest가 막는다.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update ProjectBudget b
           set b.consumedTokens = b.consumedTokens + :tokens,
               b.updatedAt = current_timestamp
         where b.project.id = :projectId
           and b.period = :period
           and b.deletedAt is null
        """,
    )
    fun settleTokens(
        @Param("projectId") projectId: Long,
        @Param("period") period: BudgetPeriod,
        @Param("tokens") tokens: Long,
    ): Int

    // provider 호출이 실패했을 때 선점분을 되돌린다.
    // 음수로 내려가면 이후 판정이 전부 어긋나므로 0에서 멈춘다.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update ProjectBudget b
           set b.consumedRequests = case when b.consumedRequests > 0 then b.consumedRequests - 1 else 0 end,
               b.updatedAt = current_timestamp
         where b.project.id = :projectId
           and b.period = :period
           and b.deletedAt is null
        """,
    )
    fun releaseRequest(
        @Param("projectId") projectId: Long,
        @Param("period") period: BudgetPeriod,
    ): Int
}
