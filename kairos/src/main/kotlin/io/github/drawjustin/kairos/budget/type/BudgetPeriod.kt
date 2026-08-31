package io.github.drawjustin.kairos.budget.type

import java.time.Instant
import java.time.ZonedDateTime

// 한도가 초기화되는 주기다. 기준 시각대는 호출하는 쪽이 정해서 넘긴다.
enum class BudgetPeriod {
    DAILY {
        override fun startOf(at: ZonedDateTime): Instant =
            at.toLocalDate().atStartOfDay(at.zone).toInstant()
    },
    MONTHLY {
        override fun startOf(at: ZonedDateTime): Instant =
            at.toLocalDate().withDayOfMonth(1).atStartOfDay(at.zone).toInstant()
    },
    ;

    // 주어진 시각이 속한 기간의 시작점. 저장된 period_started_at보다 크면 기간이 넘어간 것이다.
    abstract fun startOf(at: ZonedDateTime): Instant
}
