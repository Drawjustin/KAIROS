package io.github.drawjustin.kairos.auth.controller

import io.github.drawjustin.kairos.auth.domain.SessionMetadata
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.LogoutRequest
import io.github.drawjustin.kairos.auth.dto.MeResponse
import io.github.drawjustin.kairos.auth.dto.RefreshRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.auth.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
// 인증 관련 HTTP 요청을 AuthService로 연결하는 얇은 진입점이다.
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    // 회원가입과 동시에 첫 access/refresh 세션을 발급한다.
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        httpServletRequest: HttpServletRequest,
        @RequestHeader(name = "X-Platform", required = false) platform: String?,
        @RequestHeader(name = "X-Device", required = false) device: String?,
    ) = authService.register(request, httpServletRequest.toMetadata(platform, device))

    @PostMapping("/login")
    // 로그인 성공 시 새 refresh 세션을 만들고 access token도 함께 내려준다.
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpServletRequest: HttpServletRequest,
        @RequestHeader(name = "X-Platform", required = false) platform: String?,
        @RequestHeader(name = "X-Device", required = false) device: String?,
    ) = authService.login(request, httpServletRequest.toMetadata(platform, device))

    @PostMapping("/refresh")
    // refresh token을 검증한 뒤 rotation된 새 토큰 쌍을 발급한다.
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
        httpServletRequest: HttpServletRequest,
        @RequestHeader(name = "X-Platform", required = false) platform: String?,
        @RequestHeader(name = "X-Device", required = false) device: String?,
    ) = authService.refresh(request.refreshToken, httpServletRequest.toMetadata(platform, device))

    @PostMapping("/logout")
    // refresh 세션만 폐기해서 이후 재발급을 막는다.
    fun logout(@Valid @RequestBody request: LogoutRequest): ResponseEntity<Void> {
        authService.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    // SecurityContext에 들어간 principal을 기준으로 현재 사용자 정보를 돌려준다.
    fun me(@AuthenticationPrincipal principal: AuthenticatedUser): MeResponse {
        val user = authService.findUser(principal.id)
        val userId = requireNotNull(user.id) { "Authenticated user id must exist" }
        return MeResponse(
            id = userId,
            email = user.email,
            role = user.role,
        )
    }

    // 세션 추적과 이상 탐지에 쓸 최소한의 디바이스 정보를 요청에서 추출한다.
    private fun HttpServletRequest.toMetadata(platform: String?, device: String?) = SessionMetadata(
        platform = platform,
        device = device,
        ipAddress = getHeader("X-Forwarded-For")?.substringBefore(",")?.trim()
            ?: remoteAddr,
        userAgent = getHeader("User-Agent"),
    )
}
