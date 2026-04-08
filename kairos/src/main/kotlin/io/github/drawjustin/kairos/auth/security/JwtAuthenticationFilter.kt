package io.github.drawjustin.kairos.auth.security

import io.github.drawjustin.kairos.util.Jwt
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwt: Jwt,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        val token = header
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()

        // 이미 다른 인증 방식이 채운 컨텍스트는 덮어쓰지 않는다.
        if (!token.isNullOrBlank() && SecurityContextHolder.getContext().authentication == null) {
            try {
                val claims = jwt.parse(token)
                if (jwt.isAccessToken(claims)) {
                    // access token의 claim을 SecurityContext용 principal/authority로 옮긴다.
                    val principal = AuthenticatedUser(
                        id = jwt.userId(claims),
                        email = jwt.email(claims),
                        role = jwt.role(claims),
                    )
                    val authentication = UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
                    )
                    // 이후 컨트롤러에서는 @AuthenticationPrincipal로 이 값을 바로 꺼낼 수 있다.
                    SecurityContextHolder.getContext().authentication = authentication
                }
            } catch (_: JwtException) {
                // 유효하지 않은 토큰은 인증 없이 다음 필터로 넘긴다.
            } catch (_: IllegalArgumentException) {
            }
        }

        filterChain.doFilter(request, response)
    }
}
