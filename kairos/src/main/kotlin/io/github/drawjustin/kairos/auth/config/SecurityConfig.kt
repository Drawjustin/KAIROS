package io.github.drawjustin.kairos.auth.config

import io.github.drawjustin.kairos.auth.security.JwtAuthenticationFilter
import io.github.drawjustin.kairos.common.logging.TraceLoggingFilter
import org.springframework.core.annotation.Order
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val traceLoggingFilter: TraceLoggingFilter,
) {
    @Bean
    @Order(1)
    // 관리 엔드포인트는 별도 포트에서만 열리므로 포트 자체가 경계 역할을 한다.
    // 여기에 JWT 필터를 태우면 Prometheus가 지표를 긁어갈 때마다 토큰이 필요해져 운영이 불편해진다.
    // 대신 이 포트를 서비스 포트와 함께 외부에 노출하지 않는 것이 전제다.
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/actuator/**")
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            // 브라우저 세션 기반 앱이 아니라 JWT 기반 API라 기본 폼/세션 기능을 끈다.
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // 회원가입, 로그인, 토큰 재발급은 인증 전에도 접근 가능해야 한다.
                it.requestMatchers(
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/v1/chat/completions",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                ).permitAll()
                    // MVP 내부 tool endpoint다. 운영에서는 외부 공개 없이 내부망/게이트웨이에서만 접근하게 제한해야 한다.
                    .requestMatchers("/internal/tools/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            }
            // 가장 앞단에서 traceId와 요청 로그를 남겨 이후 로그/에러/슬랙을 연결한다.
            .addFilterBefore(traceLoggingFilter, UsernamePasswordAuthenticationFilter::class.java)
            // username/password 필터보다 먼저 JWT를 읽어 SecurityContext를 채운다.
            .addFilterAfter(jwtAuthenticationFilter, TraceLoggingFilter::class.java)
            .build()
    }

    @Bean
    // DB에는 평문 비밀번호가 아니라 bcrypt 해시만 저장한다.
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
