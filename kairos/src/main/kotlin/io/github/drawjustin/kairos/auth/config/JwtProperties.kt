package io.github.drawjustin.kairos.auth.config

import jakarta.validation.constraints.NotBlank
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "kairos.jwt")
data class JwtProperties(
    var issuer: String = "kairos",
    @field:NotBlank
    var secret: String = "",
    var accessTokenExpiration: Duration = Duration.ofMinutes(15),
    var refreshTokenExpiration: Duration = Duration.ofDays(30),
)
