package io.github.drawjustin.kairos.auth.controller

import io.github.drawjustin.kairos.auth.domain.SessionMetadata
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.LogoutResponse
import io.github.drawjustin.kairos.auth.dto.LogoutRequest
import io.github.drawjustin.kairos.auth.dto.MeResponse
import io.github.drawjustin.kairos.auth.dto.RefreshRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.auth.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "회원가입, 로그인, 토큰 재발급, 현재 사용자 조회 API")
// 인증 관련 HTTP 요청을 AuthService로 연결하는 얇은 진입점이다.
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "새 사용자를 생성하고 첫 access/refresh 토큰을 함께 발급한다.")
    // 회원가입과 동시에 첫 access/refresh 세션을 발급한다.
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        httpServletRequest: HttpServletRequest,
        @RequestHeader(name = "X-Platform", required = false) platform: String?,
        @RequestHeader(name = "X-Device", required = false) device: String?,
    ): AuthResponse = authService.register(request, httpServletRequest.toMetadata(platform, device))

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하고 새 access/refresh 토큰을 발급한다.")
    // 로그인 성공 시 새 refresh 세션을 만들고 access token도 함께 내려준다.
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpServletRequest: HttpServletRequest,
        @RequestHeader(name = "X-Platform", required = false) platform: String?,
        @RequestHeader(name = "X-Device", required = false) device: String?,
    ): AuthResponse = authService.login(request, httpServletRequest.toMetadata(platform, device))

    @PostMapping("/refresh")
    @Operation(summary = "토큰 재발급", description = "유효한 refresh token으로 새 access/refresh 토큰 쌍을 재발급한다.")
    // refresh token을 검증한 뒤 rotation된 새 토큰 쌍을 발급한다.
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
        httpServletRequest: HttpServletRequest,
        @RequestHeader(name = "X-Platform", required = false) platform: String?,
        @RequestHeader(name = "X-Device", required = false) device: String?,
    ): AuthResponse = authService.refresh(request.refreshToken, httpServletRequest.toMetadata(platform, device))

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "refresh token에 연결된 서버 측 세션을 폐기한다.")
    // refresh 세션만 폐기해서 이후 재발급을 막는다.
    fun logout(@Valid @RequestBody request: LogoutRequest): LogoutResponse {
        authService.logout(request.refreshToken)
        return LogoutResponse()
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "현재 access token 기준으로 로그인한 사용자 정보를 반환한다.")
    @SecurityRequirement(name = "bearerAuth")
    // SecurityContext에 들어간 principal을 기준으로 현재 사용자 정보를 돌려준다.
    fun me(@AuthenticationPrincipal principal: AuthenticatedUser): MeResponse =
        MeResponse(result = authService.findMe(principal.id))

    // 세션 추적과 이상 탐지에 쓸 최소한의 디바이스 정보를 요청에서 추출한다.
    private fun HttpServletRequest.toMetadata(platform: String?, device: String?) = SessionMetadata(
        platform = platform,
        device = device,
        ipAddress = getHeader("X-Forwarded-For")?.substringBefore(",")?.trim()
            ?: remoteAddr,
        userAgent = getHeader("User-Agent"),
    )
}
