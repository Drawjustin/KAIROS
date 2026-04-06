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
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        httpServletRequest: HttpServletRequest,
        @RequestHeader(name = "X-Platform", required = false) platform: String?,
        @RequestHeader(name = "X-Device", required = false) device: String?,
    ) = authService.register(request, httpServletRequest.toMetadata(platform, device))

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpServletRequest: HttpServletRequest,
        @RequestHeader(name = "X-Platform", required = false) platform: String?,
        @RequestHeader(name = "X-Device", required = false) device: String?,
    ) = authService.login(request, httpServletRequest.toMetadata(platform, device))

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
        httpServletRequest: HttpServletRequest,
        @RequestHeader(name = "X-Platform", required = false) platform: String?,
        @RequestHeader(name = "X-Device", required = false) device: String?,
    ) = authService.refresh(request.refreshToken, httpServletRequest.toMetadata(platform, device))

    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: LogoutRequest): ResponseEntity<Void> {
        authService.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: AuthenticatedUser): MeResponse {
        val user = authService.findUser(principal.id)
        val userId = requireNotNull(user.id) { "Authenticated user id must exist" }
        return MeResponse(
            id = userId,
            email = user.email,
            role = user.role,
        )
    }

    private fun HttpServletRequest.toMetadata(platform: String?, device: String?) = SessionMetadata(
        platform = platform,
        device = device,
        ipAddress = getHeader("X-Forwarded-For")?.substringBefore(",")?.trim()
            ?: remoteAddr,
        userAgent = getHeader("User-Agent"),
    )
}
