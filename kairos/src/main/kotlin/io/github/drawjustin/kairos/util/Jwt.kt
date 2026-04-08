package io.github.drawjustin.kairos.util

import io.github.drawjustin.kairos.auth.config.JwtProperties
import io.github.drawjustin.kairos.user.entity.User
import io.github.drawjustin.kairos.user.entity.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
import org.springframework.stereotype.Component

@Component
// JWT 생성과 claim 해석을 한곳에 모아 서비스 코드가 토큰 포맷을 몰라도 되게 한다.
class Jwt(
    private val properties: JwtProperties,
) {
    // jjwt는 충분히 긴 바이트 배열을 HMAC 키로 사용한다.
    private val secretKey: SecretKey =
        Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8))

    fun accessTokenExpiresAt(): Instant = Instant.now().plus(properties.accessTokenExpiration)

    fun refreshTokenExpiresAt(): Instant = Instant.now().plus(properties.refreshTokenExpiration)

    fun generateAccessToken(user: User): String {
        val userId = requireNotNull(user.id) { "User id must exist before generating an access token" }
        val expiresAt = accessTokenExpiresAt()

        // 일반 API 인증에만 쓰는 짧은 수명의 토큰이다.
        return Jwts.builder()
            .issuer(properties.issuer)
            .subject(userId.toString())
            .claim("typ", "access")
            .claim("email", user.email)
            .claim("role", user.role.name)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey)
            .compact()
    }

    fun generateRefreshToken(userId: Long): RefreshTokenIssue {
        // refresh token은 DB 세션과 연결하기 위해 별도 session id를 가진다.
        val sessionId = UUID.randomUUID().toString()
        val expiresAt = refreshTokenExpiresAt()

        val token = Jwts.builder()
            .issuer(properties.issuer)
            .subject(userId.toString())
            .claim("typ", "refresh")
            .claim("sid", sessionId)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey)
            .compact()

        return RefreshTokenIssue(
            token = token,
            sessionId = sessionId,
            expiresAt = expiresAt,
        )
    }

    fun parse(token: String): Claims =
        Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload

    // typ claim으로 access/refresh를 구분한다.
    fun isAccessToken(claims: Claims): Boolean = claims.get("typ", String::class.java) == "access"

    fun isRefreshToken(claims: Claims): Boolean = claims.get("typ", String::class.java) == "refresh"

    // subject는 항상 사용자 id 문자열로 넣어두었다.
    fun userId(claims: Claims): Long = claims.subject.toLong()

    // refresh token에만 들어 있는 세션 식별자다.
    fun sessionId(claims: Claims): String = claims.get("sid", String::class.java)

    // access token의 사용자 표시 정보는 컨텍스트 구성에 사용한다.
    fun email(claims: Claims): String = claims.get("email", String::class.java)

    fun role(claims: Claims): UserRole = UserRole.valueOf(claims.get("role", String::class.java))

    // 로그아웃처럼 예외를 삼키고 싶을 때 빠르게 유효성만 확인한다.
    fun isInvalid(token: String): Boolean =
        try {
            parse(token)
            false
        } catch (_: JwtException) {
            true
        } catch (_: IllegalArgumentException) {
            true
        }

    // refresh token 발급 결과를 DB 세션 생성에 바로 넘기기 위한 묶음이다.
    data class RefreshTokenIssue(
        val token: String,
        val sessionId: String,
        val expiresAt: Instant,
    )
}
