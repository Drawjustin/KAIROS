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

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        return createSession(user, metadata)
    }

    @Transactional
    fun refresh(refreshToken: String, metadata: SessionMetadata): AuthResponse {
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
            revokeAllActiveSessions(sessionUserId)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token mismatch")
        }

        session.markUsed()
        session.revoke()
        refreshSessionRepository.save(session)

        return createSession(session.user, metadata)
    }

    @Transactional
    fun logout(refreshToken: String) {
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
        refreshSessionRepository.findActiveSessionsByUserId(userId)
            .forEach {
                it.revoke(now)
                refreshSessionRepository.save(it)
            }
    }
}
