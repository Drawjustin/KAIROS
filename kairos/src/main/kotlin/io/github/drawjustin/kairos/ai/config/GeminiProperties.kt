package io.github.drawjustin.kairos.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Gemini Generate Content API 호출에 필요한 설정을 환경별로 분리한다.
@ConfigurationProperties(prefix = "kairos.ai.gemini")
data class GeminiProperties(
    var baseUrl: String = "https://generativelanguage.googleapis.com",
    var apiKey: String = "",
)
