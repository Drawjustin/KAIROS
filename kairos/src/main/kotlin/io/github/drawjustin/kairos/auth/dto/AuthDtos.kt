package io.github.drawjustin.kairos.auth.dto

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.user.type.UserRole
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

// 회원가입 입력은 이메일 형식과 최소 비밀번호 길이를 검증한다.
data class RegisterRequest(
    @field:Schema(description = "회원 이메일", example = "tester@example.com")
    @field:Email
    @field:NotBlank
    val email: String,

    @field:Schema(description = "회원 비밀번호", example = "password123", minLength = 8, maxLength = 100)
    @field:NotBlank
    @field:Size(min = 8, max = 100)
    val password: String,
)

// 로그인 입력은 가입된 이메일과 비밀번호 조합만 받는다.
data class LoginRequest(
    @field:Schema(description = "회원 이메일", example = "tester@example.com")
    @field:Email
    @field:NotBlank
    val email: String,

    @field:Schema(description = "회원 비밀번호", example = "password123")
    @field:NotBlank
    val password: String,
)

// refresh 요청은 기존 refresh token 원문 하나만 전달한다.
data class RefreshRequest(
    @field:Schema(description = "재발급에 사용할 refresh token", example = "eyJhbGciOiJIUzI1NiJ9.refresh-token-example")
    @field:NotBlank
    val refreshToken: String,
)

// 로그아웃도 어떤 refresh 세션을 끊을지 지정해야 한다.
data class LogoutRequest(
    @field:Schema(description = "폐기할 refresh token", example = "eyJhbGciOiJIUzI1NiJ9.refresh-token-example")
    @field:NotBlank
    val refreshToken: String,
)

// 로그인/회원가입/재발급 시 result 안에 담길 실제 인증 데이터다.
data class AuthOutput(
    @field:Schema(description = "API 인증에 사용할 access token", example = "eyJhbGciOiJIUzI1NiJ9.access-token-example")
    val accessToken: String,
    @field:Schema(description = "재발급에 사용할 refresh token", example = "eyJhbGciOiJIUzI1NiJ9.refresh-token-example")
    val refreshToken: String,
    @field:Schema(description = "Authorization 헤더에 사용할 토큰 타입", example = "Bearer")
    val tokenType: String = "Bearer",
    @field:Schema(description = "access token 만료 시각", example = "2026-04-09T00:15:00Z")
    val accessTokenExpiresAt: Instant,
    @field:Schema(description = "refresh token 만료 시각", example = "2026-05-09T00:00:00Z")
    val refreshTokenExpiresAt: Instant,
    @field:Schema(description = "서버 측 refresh 세션 식별자", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    val sessionId: String,
)

// auth 최종 응답은 result만 노출하고 에러 메타는 BaseOutput 기본값에 맡긴다.
data class AuthResponse(
    @field:Schema(description = "인증 결과 payload")
    val result: AuthOutput,
) : BaseOutput()

// /me에서 result 안에 담길 현재 사용자 정보다.
data class MeOutput(
    @field:Schema(description = "사용자 ID", example = "1")
    val id: Long,
    @field:Schema(description = "사용자 이메일", example = "tester@example.com")
    val email: String,
    @field:Schema(description = "사용자 권한", example = "USER")
    val role: UserRole,
)

// /me 최종 응답도 성공 시에는 사용자 정보만 result에 담아 내려준다.
data class MeResponse(
    @field:Schema(description = "현재 사용자 정보")
    val result: MeOutput,
) : BaseOutput()

// 로그아웃에서 result 안에 담길 최소 성공 정보다.
data class LogoutOutput(
    @field:Schema(description = "로그아웃 처리 성공 여부", example = "true")
    val success: Boolean = true,
)

// 로그아웃도 같은 응답 규격을 유지하면서 최소 성공 정보만 result에 담는다.
data class LogoutResponse(
    @field:Schema(description = "로그아웃 결과")
    val result: LogoutOutput = LogoutOutput(),
) : BaseOutput()
