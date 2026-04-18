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
    TENANT_CREATION_FORBIDDEN(HttpStatus.FORBIDDEN, "TENANT_000", "Only admins can create tenants"),
    TENANT_ALREADY_EXISTS(HttpStatus.CONFLICT, "TENANT_001", "Tenant already exists"),
    TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "TENANT_002", "Tenant not found"),
    TENANT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TENANT_003", "Access denied to tenant"),
    TENANT_USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "TENANTUSER_001", "Tenant user already exists"),
    TENANT_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "TENANTUSER_002", "Tenant user not found"),
    TENANT_USER_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "TENANTUSER_003", "Only tenant owners can manage tenant users"),
    TENANT_LAST_OWNER_REQUIRED(HttpStatus.CONFLICT, "TENANTUSER_004", "Tenant must have at least one owner"),
    PROJECT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PROJECT_001", "Project already exists"),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_002", "Project not found"),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PROJECT_003", "Access denied to project"),
    API_KEY_ALREADY_EXISTS(HttpStatus.CONFLICT, "APIKEY_001", "API key already exists"),
    AI_INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "AI_001", "Invalid API key"),
    AI_API_KEY_EXPIRED(HttpStatus.UNAUTHORIZED, "AI_002", "API key expired"),
    AI_PROJECT_INACTIVE(HttpStatus.FORBIDDEN, "AI_003", "Project is not active"),
    AI_STREAM_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "AI_004", "Streaming is not supported yet"),
    AI_PROVIDER_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "AI_005", "AI provider is not configured"),
    AI_PROVIDER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI_006", "AI provider request failed"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid input"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "Internal server error", slackError = true),
}
