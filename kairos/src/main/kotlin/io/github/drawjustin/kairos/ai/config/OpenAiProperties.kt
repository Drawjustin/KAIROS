package io.github.drawjustin.kairos.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

// OpenAI 호출에 필요한 설정을 환경별로 분리한다.
@ConfigurationProperties(prefix = "kairos.ai.openai")
data class OpenAiProperties(
    var baseUrl: String = "https://api.openai.com",
    var apiKey: String = "",
)
