package io.github.drawjustin.kairos.common.slack

import org.springframework.boot.context.properties.ConfigurationProperties

// 슬랙 웹훅 주소를 설정으로 분리해서 환경별로 안전하게 주입받는다.
@ConfigurationProperties(prefix = "slack")
data class SlackProperties(
    val webhookUrl: String = "",
)
