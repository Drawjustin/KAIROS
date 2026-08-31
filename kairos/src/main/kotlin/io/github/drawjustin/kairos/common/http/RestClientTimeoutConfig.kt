package io.github.drawjustin.kairos.common.http

import io.github.drawjustin.kairos.ai.config.AiHttpProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
// 애플리케이션이 주입받는 RestClient.Builder에 공통 타임아웃을 입힌다.
// 어댑터마다 따로 설정하면 새 provider를 추가할 때 빠뜨리기 쉬우므로 한곳에서 강제한다.
class RestClientTimeoutConfig {
    @Bean
    fun restClientTimeoutCustomizer(aiHttpProperties: AiHttpProperties): RestClientCustomizer =
        RestClientCustomizer { builder ->
            val settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(aiHttpProperties.connectTimeout)
                .withReadTimeout(aiHttpProperties.readTimeout)
            builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        }
}
