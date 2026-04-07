package io.github.drawjustin.kairos.auth.config

import jakarta.validation.constraints.NotBlank
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
// application.yaml의 kairos.jwt.* 값을 타입 안전하게 바인딩한다.
@ConfigurationProperties(prefix = "kairos.jwt")
data class JwtProperties(
    // 발급자 정보는 토큰 검증과 디버깅 시 출처를 구분하는 데 쓴다.
    var issuer: String = "kairos",
    @field:NotBlank
    var secret: String = "",
    // access token은 짧게 유지해서 탈취 위험을 줄인다.
    var accessTokenExpiration: Duration = Duration.ofMinutes(15),
    // refresh token은 길게 유지하되 DB 세션과 함께 통제한다.
    var refreshTokenExpiration: Duration = Duration.ofDays(30),
)
