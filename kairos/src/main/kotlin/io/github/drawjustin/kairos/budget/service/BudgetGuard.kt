package io.github.drawjustin.kairos.budget.service

import io.github.drawjustin.kairos.budget.config.BudgetProperties
import io.github.drawjustin.kairos.budget.repository.ProjectBudgetRepository
import io.github.drawjustin.kairos.budget.type.BudgetPeriod
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import java.time.ZonedDateTime
import org.springframework.stereotype.Service

@Service
// 예산 통제 진입점이다. 카드 결제의 승인·매입·취소와 같은 3단계로 동작한다.
//
//   reserve  호출 전에 건수를 선점한다. 토큰은 호출 전에 알 수 없으므로 건수만 잡는다.
//   settle   응답이 온 뒤 실제 토큰으로 정산한다.
//   release  호출이 실패하면 선점분을 되돌린다.
//
// 이 클래스는 트랜잭션을 열지 않는다. 각 UPDATE가 리포지토리에서 자기만의 짧은 트랜잭션으로
// 끝나야 AI 응답을 기다리는 동안 예산 행의 잠금이 남지 않는다.
class BudgetGuard(
    private val projectBudgetRepository: ProjectBudgetRepository,
    private val budgetProperties: BudgetProperties,
) {
    // 한 project가 일 한도와 월 한도를 함께 둘 수 있으므로 모든 기간을 선점해야 통과다.
    // 중간에 하나라도 막히면 앞서 선점한 기간을 되돌린다. 되돌리지 않으면
    // 실제로는 거절된 요청이 다른 기간의 한도만 갉아먹는다.
    fun reserve(projectId: Long): BudgetReservation {
        val budgets = projectBudgetRepository.findAllByProject_IdAndDeletedAtIsNullOrderByPeriodAsc(projectId)
        if (budgets.isEmpty()) {
            return BudgetReservation.none(projectId)
        }

        val now = ZonedDateTime.now(budgetProperties.zone)
        val reservedPeriods = mutableListOf<BudgetPeriod>()
        budgets.forEach { budget ->
            val period = budget.period
            projectBudgetRepository.resetExpiredPeriod(projectId, period, period.startOf(now))

            if (projectBudgetRepository.reserveRequest(projectId, period) == 0) {
                releasePeriods(projectId, reservedPeriods)
                throw KairosException(
                    KairosErrorCode.AI_BUDGET_EXCEEDED,
                    "${period.name} 예산 한도를 초과했습니다",
                )
            }
            reservedPeriods += period
        }
        return BudgetReservation(projectId, reservedPeriods)
    }

    // 실제 사용량을 원장에 반영한다. 한도를 넘겨도 기록은 남기고, 차단은 다음 요청의 선점이 맡는다.
    fun settle(reservation: BudgetReservation, totalTokens: Int) {
        if (!reservation.reserved || totalTokens <= 0) {
            return
        }
        reservation.periods.forEach { period ->
            projectBudgetRepository.settleTokens(reservation.projectId, period, totalTokens.toLong())
        }
    }

    // provider 호출이 실패했을 때 선점분을 되돌린다.
    fun release(reservation: BudgetReservation) {
        if (!reservation.reserved) {
            return
        }
        releasePeriods(reservation.projectId, reservation.periods)
    }

    private fun releasePeriods(projectId: Long, periods: List<BudgetPeriod>) {
        periods.forEach { projectBudgetRepository.releaseRequest(projectId, it) }
    }
}
