package io.github.drawjustin.kairos.auth.service

import io.github.drawjustin.kairos.auth.domain.RefreshSession
import io.github.drawjustin.kairos.auth.domain.SessionMetadata
import io.github.drawjustin.kairos.auth.dto.AuthOutput
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.MeOutput
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.auth.repository.RefreshSessionRepository
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.drawjustin.kairos.auth.security.Jwt
import io.github.drawjustin.kairos.user.entity.User
import io.github.drawjustin.kairos.user.type.UserRole
import io.github.drawjustin.kairos.user.repository.UserRepository
import io.jsonwebtoken.JwtException
import java.time.Instant
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
// 회원가입, 로그인, refresh rotation, 로그아웃을 한곳에서 관리한다.
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
        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw KairosException(KairosErrorCode.EMAIL_ALREADY_IN_USE)
        }

        val newUser = User(
            email = normalizedEmail,
            password = passwordEncoder.encode(request.password),
            role = UserRole.USER,
        )
        val user: User = userRepository.save(newUser)

        // 첫 로그인과 같은 효과로 바로 세션을 만들어 UX를 단순하게 한다.
        return createSession(user, metadata)
    }

    @Transactional
    fun login(request: LoginRequest, metadata: SessionMetadata): AuthResponse {
        val user = userRepository.findByEmailAndDeletedAtIsNull(request.email.trim().lowercase())
            .orElseThrow { KairosException(KairosErrorCode.INVALID_CREDENTIALS) }

        // 비밀번호가 틀리면 토큰 발급 없이 바로 종료한다.
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw KairosException(KairosErrorCode.INVALID_CREDENTIALS)
        }

        // 로그인 성공 시 기존 세션과 별개로 새 refresh 세션을 발급한다.
        return createSession(user, metadata)
    }

    @Transactional
    fun refresh(refreshToken: String, metadata: SessionMetadata): AuthResponse {
        // 먼저 JWT 자체가 정상인지 확인한다.
        val claims = try {
            jwt.parse(refreshToken)
        } catch (_: JwtException) {
            throw KairosException(KairosErrorCode.INVALID_REFRESH_TOKEN)
        } catch (_: IllegalArgumentException) {
            throw KairosException(KairosErrorCode.INVALID_REFRESH_TOKEN)
        }

        if (!jwt.isRefreshToken(claims)) {
            throw KairosException(KairosErrorCode.INVALID_REFRESH_TOKEN)
        }

        val sessionId = jwt.sessionId(claims)
        val session = refreshSessionRepository.findBySessionIdAndDeletedAtIsNullAndUser_DeletedAtIsNull(sessionId)
            ?: throw KairosException(KairosErrorCode.REFRESH_SESSION_NOT_FOUND)

        val sessionUserId = requireNotNull(session.user.id) { "Refresh session user id must exist" }
        if (session.revokedAt != null) {
            // 이미 폐기된 refresh token 재사용은 탈취 시도로 보고 모든 활성 세션을 끊는다.
            revokeAllActiveSessions(sessionUserId)
            throw KairosException(KairosErrorCode.REFRESH_TOKEN_REUSE_DETECTED)
        }

        if (session.expiresAt.isBefore(Instant.now())) {
            // 만료된 세션은 다음 요청부터도 바로 거부되도록 폐기 상태로 남긴다.
            session.revoke()
            refreshSessionRepository.save(session)
            throw KairosException(KairosErrorCode.REFRESH_TOKEN_EXPIRED)
        }

        val hashedToken = tokenHasher.hash(refreshToken)
        if (session.tokenHash != hashedToken) {
            // 세션 id는 맞지만 토큰 원문이 다르면 위조 또는 재사용 가능성이 있다.
            revokeAllActiveSessions(sessionUserId)
            throw KairosException(KairosErrorCode.REFRESH_TOKEN_MISMATCH)
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

        val session = refreshSessionRepository.findBySessionIdAndDeletedAtIsNullAndUser_DeletedAtIsNull(jwt.sessionId(claims)) ?: return
        if (session.tokenHash == tokenHasher.hash(refreshToken) && session.revokedAt == null) {
            session.revoke()
            refreshSessionRepository.save(session)
        }
    }

    @Transactional(readOnly = true)
    fun findMe(userId: Long): MeOutput {
        // open-in-view를 끈 뒤에는 컨트롤러가 엔티티를 만지지 않도록 DTO로 바로 바꿔서 반환한다.
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow { KairosException(KairosErrorCode.USER_NOT_FOUND) }

        val resolvedUserId = requireNotNull(user.id) { "Authenticated user id must exist" }
        return MeOutput(
            id = resolvedUserId,
            email = user.email,
            role = user.role,
        )
    }

    private fun createSession(user: User, metadata: SessionMetadata): AuthResponse {
        val userId = requireNotNull(user.id) { "User id must exist before creating a session" }
        // access token은 stateless, refresh token은 DB 세션과 함께 발급한다.
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

        // 클라이언트는 원문 refresh token을 지금 응답에서만 받을 수 있다.
        return AuthResponse(
            result = AuthOutput(
                accessToken = jwt.generateAccessToken(user),
                refreshToken = refreshIssue.token,
                accessTokenExpiresAt = jwt.accessTokenExpiresAt(),
                refreshTokenExpiresAt = refreshIssue.expiresAt,
                sessionId = savedSession.sessionId,
            ),
        )
    }

    private fun revokeAllActiveSessions(userId: Long) {
        val now = Instant.now()
        // 재사용 탐지 시점에는 남아 있는 활성 세션을 모두 폐기한다.
        refreshSessionRepository.findAllByUser_IdAndUser_DeletedAtIsNullAndRevokedAtIsNullAndDeletedAtIsNull(userId)
            .forEach {
                it.revoke(now)
                refreshSessionRepository.save(it)
            }
    }
}
