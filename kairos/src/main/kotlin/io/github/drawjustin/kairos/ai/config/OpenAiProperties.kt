package io.github.drawjustin.kairos.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties

// 첫 버전은 OpenAI 하나만 붙여 공통 API의 호출선과 응답 형태를 먼저 고정한다.
@ConfigurationProperties(prefix = "kairos.ai.openai")
data class OpenAiProperties(
    var baseUrl: String = "https://api.openai.com",
    var apiKey: String = "",
)
