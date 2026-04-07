package io.github.drawjustin.kairos.common.error

import org.springframework.http.HttpStatus

// 비즈니스 에러를 상태코드, 응답 코드, 기본 메시지와 함께 한곳에서 관리한다.
enum class KairosErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
    // true면 이후 전역 예외 처리에서 슬랙 알림 대상으로 확장할 수 있다.
    val slackError: Boolean = false,
) {
    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT, "AUTH_001", "Email already in use"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_002", "Invalid credentials"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "Invalid refresh token"),
    REFRESH_SESSION_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_004", "Refresh session not found"),
    REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "AUTH_005", "Refresh token reuse detected", slackError = true),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_006", "Refresh token expired"),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_007", "Refresh token mismatch", slackError = true),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "User not found"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid input"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "Internal server error", slackError = true),
}
