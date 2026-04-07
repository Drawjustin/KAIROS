package io.github.drawjustin.kairos.auth.config

import io.github.drawjustin.kairos.auth.security.JwtAuthenticationFilter
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
) {
    @Bean
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
                ).permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            }
            // username/password 필터보다 먼저 JWT를 읽어 SecurityContext를 채운다.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    // DB에는 평문 비밀번호가 아니라 bcrypt 해시만 저장한다.
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
