package io.github.drawjustin.kairos.auth.service

import io.github.drawjustin.kairos.auth.domain.RefreshSession
import io.github.drawjustin.kairos.auth.domain.SessionMetadata
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.auth.repository.RefreshSessionRepository
import io.github.drawjustin.kairos.user.entity.User
import io.github.drawjustin.kairos.user.repository.UserRepository
import io.github.drawjustin.kairos.util.Jwt
import io.jsonwebtoken.JwtException
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshSessionRepository: RefreshSessionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenHasher: TokenHasher,
    private val jwt: Jwt,
) {
    @Transactional
    fun register(request: RegisterRequest, metadata: SessionMetadata): AuthResponse {
        // 이메일은 비교 일관성을 위해 저장 전에 정규화한다.
        val normalizedEmail = request.email.trim().lowercase()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
        }

        val newUser = User(
            email = normalizedEmail,
            password = passwordEncoder.encode(request.password),
        )
        val user: User = userRepository.save(newUser)

        return createSession(user, metadata)
    }

    @Transactional
    fun login(request: LoginRequest, metadata: SessionMetadata): AuthResponse {
        val user = userRepository.findByEmail(request.email.trim().lowercase())
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials") }

        // 비밀번호가 틀리면 토큰 발급 없이 바로 종료한다.
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        return createSession(user, metadata)
    }

    @Transactional
    fun refresh(refreshToken: String, metadata: SessionMetadata): AuthResponse {
        // 먼저 JWT 자체가 정상인지 확인한다.
        val claims = try {
            jwt.parse(refreshToken)
        } catch (_: JwtException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }

        if (!jwt.isRefreshToken(claims)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }

        val sessionId = jwt.sessionId(claims)
        val session = refreshSessionRepository.findBySessionId(sessionId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh session not found")

        val sessionUserId = requireNotNull(session.user.id) { "Refresh session user id must exist" }
        if (session.revokedAt != null) {
            // 이미 폐기된 refresh token 재사용은 탈취 시도로 보고 모든 활성 세션을 끊는다.
            revokeAllActiveSessions(sessionUserId)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected")
        }

        if (session.expiresAt.isBefore(Instant.now())) {
            session.revoke()
            refreshSessionRepository.save(session)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired")
        }

        val hashedToken = tokenHasher.hash(refreshToken)
        if (session.tokenHash != hashedToken) {
            // 세션 id는 맞지만 토큰 원문이 다르면 위조 또는 재사용 가능성이 있다.
            revokeAllActiveSessions(sessionUserId)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token mismatch")
        }

        // refresh rotation: 기존 refresh는 폐기하고 새 세션으로 교체한다.
        session.markUsed()
        session.revoke()
        refreshSessionRepository.save(session)

        return createSession(session.user, metadata)
    }

    @Transactional
    fun logout(refreshToken: String) {
        // 로그아웃은 idempotent하게 처리해서 이미 무효한 토큰이어도 조용히 끝낸다.
        if (jwt.isInvalid(refreshToken)) {
            return
        }

        val claims = jwt.parse(refreshToken)
        if (!jwt.isRefreshToken(claims)) {
            return
        }

        val session = refreshSessionRepository.findBySessionId(jwt.sessionId(claims)) ?: return
        if (session.tokenHash == tokenHasher.hash(refreshToken) && session.revokedAt == null) {
            session.revoke()
            refreshSessionRepository.save(session)
        }
    }

    @Transactional(readOnly = true)
    fun findUser(userId: Long): User {
        return userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
    }

    private fun createSession(user: User, metadata: SessionMetadata): AuthResponse {
        val userId = requireNotNull(user.id) { "User id must exist before creating a session" }
        val refreshIssue = jwt.generateRefreshToken(userId)
        // 서버는 refresh token 원문 대신 해시만 저장한다.
        val refreshSession = RefreshSession(
            user = user,
            sessionId = refreshIssue.sessionId,
            tokenHash = tokenHasher.hash(refreshIssue.token),
            expiresAt = refreshIssue.expiresAt,
            platform = metadata.platform,
            device = metadata.device,
            ipAddress = metadata.ipAddress,
            userAgent = metadata.userAgent,
        )
        val savedSession: RefreshSession = refreshSessionRepository.save(refreshSession)

        return AuthResponse(
            accessToken = jwt.generateAccessToken(user),
            refreshToken = refreshIssue.token,
            accessTokenExpiresAt = jwt.accessTokenExpiresAt(),
            refreshTokenExpiresAt = refreshIssue.expiresAt,
            sessionId = savedSession.sessionId,
        )
    }

    private fun revokeAllActiveSessions(userId: Long) {
        val now = Instant.now()
        // 재사용 탐지 시점에는 남아 있는 활성 세션을 모두 폐기한다.
        refreshSessionRepository.findAllByUser_IdAndRevokedAtIsNull(userId)
            .forEach {
                it.revoke(now)
                refreshSessionRepository.save(it)
            }
    }
}
