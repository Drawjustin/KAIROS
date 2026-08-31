package io.github.drawjustin.kairos.budget.config

import java.time.ZoneId
import org.springframework.boot.context.properties.ConfigurationProperties

// 한도가 초기화되는 기준 시각대다.
// 일 한도는 "누구 기준의 자정인가"에 따라 결과가 달라지므로 환경별로 바꿀 수 있게 열어둔다.
@ConfigurationProperties(prefix = "kairos.budget")
data class BudgetProperties(
    var zone: ZoneId = ZoneId.of("Asia/Seoul"),
)
