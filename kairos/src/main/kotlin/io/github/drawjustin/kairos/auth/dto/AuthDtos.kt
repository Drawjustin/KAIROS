package io.github.drawjustin.kairos.auth.dto

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.user.entity.UserRole
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

// 로그인/회원가입/재발급 시 result 안에 담길 실제 인증 데이터다.
data class AuthOutput(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val sessionId: String,
)

// auth 최종 응답은 result만 노출하고 에러 메타는 BaseOutput 기본값에 맡긴다.
data class AuthResponse(
    val result: AuthOutput,
) : BaseOutput()

// /me에서 result 안에 담길 현재 사용자 정보다.
data class MeOutput(
    val id: Long,
    val email: String,
    val role: UserRole,
)

// /me 최종 응답도 성공 시에는 사용자 정보만 result에 담아 내려준다.
data class MeResponse(
    val result: MeOutput,
) : BaseOutput()

// 로그아웃에서 result 안에 담길 최소 성공 정보다.
data class LogoutOutput(
    val success: Boolean = true,
)

// 로그아웃도 같은 응답 규격을 유지하면서 최소 성공 정보만 result에 담는다.
data class LogoutResponse(
    val result: LogoutOutput = LogoutOutput(),
) : BaseOutput()
