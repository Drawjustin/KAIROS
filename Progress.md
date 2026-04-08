# Kairos

Spring Boot + Kotlin 기반의 JWT 인증 API 프로젝트입니다.

현재는 인증 흐름과 공통 백엔드 기반 작업을 먼저 정리한 상태입니다.

## Tech Stack

- Java 21
- Kotlin 1.9.25
- Spring Boot 3.5.13
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Testcontainers
- springdoc-openapi / Swagger UI

## Current Status

현재 구현/정리된 항목:

- JWT 기반 인증
  - 회원가입
  - 로그인
  - access token / refresh token 발급
  - refresh rotation
  - refresh token 재사용 탐지
  - 로그아웃 시 refresh session 폐기
- 공통 응답 / 에러 포맷
  - 성공 응답은 `result`
  - 에러 응답은 `errorCode`, `errorMessage`
- 공통 예외 처리
  - `KairosException`
  - `KairosErrorCode`
  - `GlobalExceptionHandler`
- 로깅 / 추적
  - 요청 단위 `traceId`
  - 응답 헤더 `X-Trace-Id`
  - 요청 완료 로그
  - 예외 로그 공통화
- Slack 에러 알림
  - 지정된 중요 에러만 Slack webhook 전송
- JPA 공통 기반
  - `BaseEntity`
  - `createdAt`, `updatedAt`, `deletedAt`
  - JPA Auditing 활성화
- Soft delete 정책
  - `deleted_at` 기반 soft delete
  - 기본 조회 시 deleted row 제외
  - 활성 사용자만 email unique
- 보안 설정 정리
  - `open-in-view=false`
  - Spring Security 기본 generated password 제거
- Swagger / OpenAPI 연동
  - Swagger UI 접근 가능
  - Bearer JWT 인증 스킴 설정

## API Summary

인증 API:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

## Response Shape

성공 응답 예시:

```json
{
  "result": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer"
  }
}
```

에러 응답 예시:

```json
{
  "errorCode": "AUTH_003",
  "errorMessage": "Invalid refresh token"
}
```

추적용 `traceId`는 body가 아니라 응답 헤더 `X-Trace-Id`로 제공합니다.

## JWT / Refresh Policy

- access token은 짧은 수명의 stateless 토큰입니다.
- refresh token은 DB의 `refresh_sessions`와 함께 관리합니다.
- refresh 요청 시 refresh rotation을 수행합니다.
- 이미 폐기된 refresh token 재사용이 감지되면 사용자 활성 세션을 모두 revoke합니다.

## Soft Delete Policy

- `users`, `refresh_sessions`는 soft delete를 사용합니다.
- 삭제 시 실제 `DELETE` 대신 `deleted_at`이 기록됩니다.
- 기본 조회에서는 `deleted_at is null` 데이터만 조회합니다.
- `users.email`은 활성 사용자끼리만 unique합니다.
- 추후 purge job으로 오래된 soft deleted row를 hard delete 할 수 있습니다.

## Logging / Trace

- 모든 응답 헤더에 `X-Trace-Id`를 포함합니다.
- 예외 발생 시 서버 로그에서 같은 `traceId`로 검색할 수 있습니다.
- 콘솔 로그 패턴에 MDC traceId가 포함되어 있습니다.

## Swagger

로컬 실행 후 접근:

- `http://localhost:8080/swagger-ui/index.html`
- `http://localhost:8080/v3/api-docs`

설정 요약:

- `springdoc-openapi-starter-webmvc-ui` 사용
- `/swagger-ui/**`, `/v3/api-docs/**`는 security에서 `permitAll`
- Bearer JWT 인증 스킴 등록

운영에서는 Swagger 비활성화를 권장합니다.

예시:

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

## Package Structure

현재 구조는 상위는 기능별, 하위는 역할별로 나뉘어 있습니다.

- `auth`
  - `config`
  - `controller`
  - `domain`
  - `dto`
  - `repository`
  - `security`
  - `service`
- `user`
  - `controller`
  - `entity`
  - `repository`
  - `service`
- `common`
  - `api`
  - `error`
  - `logging`
  - `persistence`
  - `slack`

## Local Run

기본 프로필은 `local`입니다.

주요 환경변수:

- `KAIROS_API_DB_URL`
- `KAIROS_API_DB_USERNAME`
- `KAIROS_API_DB_PASSWORD`
- `KAIROS_JWT_SECRET`
- `KAIROS_JWT_ISSUER` (optional, default: `kairos`)
- `KAIROS_JWT_ACCESS_TOKEN_EXPIRATION` (optional, default: `15m`)
- `KAIROS_JWT_REFRESH_TOKEN_EXPIRATION` (optional, default: `30d`)
- `SLACK_WEBHOOK_URL` (optional)

예시:

```bash
./gradlew bootRun
```

## Test

인증 통합 테스트는 다음을 검증합니다.

- 회원가입 / 로그인 / 내 정보 조회
- refresh rotation
- logout 후 refresh 차단
- 입력 검증
- soft deleted user 로그인 차단
- soft delete 후 동일 이메일 재가입 허용
- soft deleted refresh session 차단

실행 예시:

```bash
./gradlew test --tests io.github.drawjustin.kairos.auth.AuthIntegrationTests
```

## Important Design Decisions

- `open-in-view=false`
  - 서비스 계층 안에서 필요한 데이터를 모두 조회하고 DTO로 변환합니다.
- traceId는 헤더 전용
  - 클라이언트는 `X-Trace-Id`를 서버 담당자에게 전달하면 됩니다.
- `UserRole`은 enum 사용
  - 문자열 하드코딩 대신 타입 안전성 확보
- Swagger 취약점 대응
  - `commons-lang3`는 `3.18.0`으로 명시 고정

## Next Priorities

현재 남은 우선순위:

1. 전체 빌드 / 테스트 실제 검증
2. Swagger 문서 예시값 / 설명 보강
3. 운영용 설정 분리
   - prod에서 Swagger 비활성화
   - Slack / logging 수준 점검
4. soft delete purge 전략 구체화
5. 관리자 기능 또는 권한 확장 설계
