package io.github.drawjustin.kairos

import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
// JwtProperties 같은 설정 객체를 @ConfigurationProperties로 자동 등록한다.
@ConfigurationPropertiesScan
class KairosApplication

fun main(args: Array<String>) {
	runApplication<KairosApplication>(*args)
}
