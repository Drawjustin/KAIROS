package io.github.drawjustin.kairos.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Claude Messages API 호출에 필요한 Anthropic 설정을 환경별로 분리한다.
@ConfigurationProperties(prefix = "kairos.ai.anthropic")
data class AnthropicProperties(
    var baseUrl: String = "https://api.anthropic.com",
    var apiKey: String = "",
    var version: String = "2023-06-01",
)
