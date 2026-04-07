package io.github.drawjustin.kairos.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

// 회원가입 입력은 이메일 형식과 최소 비밀번호 길이를 검증한다.
data class RegisterRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 100)
    val password: String,
)

// 로그인 입력은 가입된 이메일과 비밀번호 조합만 받는다.
data class LoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String,
)

// refresh 요청은 기존 refresh token 원문 하나만 전달한다.
data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)

// 로그아웃도 어떤 refresh 세션을 끊을지 지정해야 한다.
data class LogoutRequest(
    @field:NotBlank
    val refreshToken: String,
)

// 로그인/회원가입/재발급 성공 시 클라이언트가 바로 사용할 토큰 묶음이다.
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val sessionId: String,
)

// /me 응답은 인증된 사용자의 최소 공개 정보만 내려준다.
data class MeResponse(
    val id: Long,
    val email: String,
    val role: String,
)
