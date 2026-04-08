package io.github.drawjustin.kairos.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
// 요청마다 traceId를 부여하고, 기본 access log를 한 줄로 남긴다.
class TraceLoggingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = request.getHeader(TRACE_ID_HEADER)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().replace("-", "")
        val startedAt = System.currentTimeMillis()

        MDC.put(TRACE_ID_KEY, traceId)
        response.setHeader(TRACE_ID_HEADER, traceId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = System.currentTimeMillis() - startedAt
            log.info(
                "Request completed method={} path={} status={} durationMs={}",
                request.method,
                request.requestURI,
                response.status,
                durationMs,
            )
            // 서블릿 컨테이너 스레드가 재사용되기 전에 요청별 문맥을 비운다.
            MDC.clear()
        }
    }

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
    }
}
