package io.github.drawjustin.kairos.budget.service

import io.github.drawjustin.kairos.budget.type.BudgetPeriod

// 선점에 성공한 기간 목록이다. 정산과 보상은 이 목록을 그대로 되짚어 수행한다.
// periods가 비어 있으면 예산을 설정하지 않은 project라 통제할 대상이 없다는 뜻이다.
data class BudgetReservation(
    val projectId: Long,
    val periods: List<BudgetPeriod>,
) {
    val reserved: Boolean get() = periods.isNotEmpty()

    companion object {
        fun none(projectId: Long): BudgetReservation = BudgetReservation(projectId, emptyList())
    }
}
