package io.github.drawjustin.kairos.util

import io.github.drawjustin.kairos.auth.config.JwtProperties
import io.github.drawjustin.kairos.user.entity.User
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
            .claim("role", user.role)
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

    fun userId(claims: Claims): Long = claims.subject.toLong()

    fun sessionId(claims: Claims): String = claims.get("sid", String::class.java)

    fun email(claims: Claims): String = claims.get("email", String::class.java)

    fun role(claims: Claims): String = claims.get("role", String::class.java)

    fun isInvalid(token: String): Boolean =
        try {
            parse(token)
            false
        } catch (_: JwtException) {
            true
        } catch (_: IllegalArgumentException) {
            true
        }

    data class RefreshTokenIssue(
        val token: String,
        val sessionId: String,
        val expiresAt: Instant,
    )
}
