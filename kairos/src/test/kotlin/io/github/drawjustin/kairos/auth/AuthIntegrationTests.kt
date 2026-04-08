package io.github.drawjustin.kairos.auth

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.auth.dto.AuthOutput
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.LogoutResponse
import io.github.drawjustin.kairos.auth.dto.LogoutRequest
import io.github.drawjustin.kairos.auth.dto.MeResponse
import io.github.drawjustin.kairos.auth.dto.RefreshRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.auth.repository.RefreshSessionRepository
import java.time.Instant
import io.github.drawjustin.kairos.user.entity.UserRole
import io.github.drawjustin.kairos.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
// 실제 HTTP 요청, Security, JWT, DB 세션이 함께 맞물리는 인증 시나리오를 검증한다.
class AuthIntegrationTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var refreshSessionRepository: RefreshSessionRepository

    @BeforeEach
    fun setUp() {
        // 테스트 간 데이터 누수를 막기 위해 매번 초기화한다.
        refreshSessionRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    @Test
    fun `register creates a user and refresh session`() {
        val response = register(
            RegisterRequest(
                email = "tester@example.com",
                password = "password123",
            ),
        )

        assertThat(response.accessToken).isNotBlank()
        assertThat(response.refreshToken).isNotBlank()
        assertThat(response.tokenType).isEqualTo("Bearer")
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("tester@example.com")).isPresent
        assertThat(refreshSessionRepository.findBySessionIdAndDeletedAtIsNullAndUser_DeletedAtIsNull(response.sessionId)).isNotNull
    }

    @Test
    fun `me returns the authenticated user`() {
        register(
            RegisterRequest(
                email = "me@example.com",
                password = "password123",
            ),
        )

        val loginResponse = login(
            LoginRequest(
                email = "me@example.com",
                password = "password123",
            ),
        )

        val result = mockMvc.perform(
            get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${loginResponse.accessToken}"),
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = objectMapper.readValue(result.response.contentAsByteArray, MeResponse::class.java)
        assertThat(response.result.email).isEqualTo("me@example.com")
        assertThat(response.result.role).isEqualTo(UserRole.USER)
    }

    @Test
    fun `refresh rotates the refresh token`() {
        register(
            RegisterRequest(
                email = "rotate@example.com",
                password = "password123",
            ),
        )

        val loginResponse = login(
            LoginRequest(
                email = "rotate@example.com",
                password = "password123",
            ),
        )

        val refreshResponse = refresh(RefreshRequest(loginResponse.refreshToken))

        // access token 문자열 자체는 같은 초에 발급되면 같을 수 있으므로,
        // rotation의 핵심인 refresh token 교체와 재사용 차단을 검증한다.
        assertThat(refreshResponse.refreshToken).isNotEqualTo(loginResponse.refreshToken)
        assertThat(refreshResponse.sessionId).isNotEqualTo(loginResponse.sessionId)

        mockMvc.perform(
            get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${refreshResponse.accessToken}"),
        )
            .andExpect(status().isOk)

        // rotation 이후 이전 refresh token은 다시 사용할 수 없어야 한다.
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(RefreshRequest(loginResponse.refreshToken))),
        )
            .assertUnauthorized("Refresh token reuse detected")
    }

    @Test
    fun `logout revokes the refresh token`() {
        register(
            RegisterRequest(
                email = "logout@example.com",
                password = "password123",
            ),
        )

        val loginResponse = login(
            LoginRequest(
                email = "logout@example.com",
                password = "password123",
            ),
        )

        mockMvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(LogoutRequest(loginResponse.refreshToken))),
        )
            .andExpect(status().isOk)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, LogoutResponse::class.java)
                assertThat(response.result.success).isTrue()
            }

        // 로그아웃한 refresh token으로는 더 이상 재발급되면 안 된다.
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(RefreshRequest(loginResponse.refreshToken))),
        )
            .assertUnauthorized("Refresh token reuse detected")
    }

    @Test
    fun `register rejects short passwords`() {
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        RegisterRequest(
                            email = "tester@example.com",
                            password = "1234",
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorCode).isEqualTo(KairosErrorCode.INVALID_INPUT.code)
            }
    }

    @Test
    fun `login rejects a soft deleted user`() {
        register(
            RegisterRequest(
                email = "deleted-user@example.com",
                password = "password123",
            ),
        )

        val user = userRepository.findByEmailAndDeletedAtIsNull("deleted-user@example.com").orElseThrow()
        userRepository.delete(user)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        LoginRequest(
                            email = "deleted-user@example.com",
                            password = "password123",
                        ),
                    ),
                ),
        )
            .assertUnauthorized(KairosErrorCode.INVALID_CREDENTIALS.message)
    }

    @Test
    fun `register allows reusing an email after soft delete`() {
        register(
            RegisterRequest(
                email = "rejoin@example.com",
                password = "password123",
            ),
        )

        val deletedUser = userRepository.findByEmailAndDeletedAtIsNull("rejoin@example.com").orElseThrow()
        userRepository.delete(deletedUser)

        val response = register(
            RegisterRequest(
                email = "rejoin@example.com",
                password = "password123",
            ),
        )

        assertThat(response.accessToken).isNotBlank()
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("rejoin@example.com")).isPresent
    }

    @Test
    fun `refresh rejects a soft deleted session`() {
        register(
            RegisterRequest(
                email = "deleted-session@example.com",
                password = "password123",
            ),
        )

        val loginResponse = login(
            LoginRequest(
                email = "deleted-session@example.com",
                password = "password123",
            ),
        )

        val session = refreshSessionRepository
            .findBySessionIdAndDeletedAtIsNullAndUser_DeletedAtIsNull(loginResponse.sessionId)
            ?: error("Expected refresh session to exist")
        session.deletedAt = Instant.now()
        refreshSessionRepository.save(session)

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(RefreshRequest(loginResponse.refreshToken))),
        )
            .assertUnauthorized(KairosErrorCode.REFRESH_SESSION_NOT_FOUND.message)
    }

    private fun register(request: RegisterRequest): AuthOutput {
        // 테스트 본문에서는 핵심 시나리오만 보이도록 HTTP 호출 세부는 헬퍼로 감싼다.
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsByteArray, AuthResponse::class.java).result
    }

    private fun login(request: LoginRequest): AuthOutput {
        // 로그인 성공 응답을 객체로 바로 역직렬화해 테스트 가독성을 높인다.
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsByteArray, AuthResponse::class.java).result
    }

    private fun refresh(request: RefreshRequest): AuthOutput {
        // refresh 재발급 시나리오도 동일한 방식으로 재사용 가능하게 뽑아둔다.
        val result = mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsByteArray, AuthResponse::class.java).result
    }

    private fun ResultActions.assertUnauthorized(expectedMessage: String) {
        // 공통 에러 응답 포맷이 적용되었는지 함께 검증한다.
        andExpect(status().isUnauthorized)
            .andExpect { result ->
                val response = objectMapper.readValue(result.response.contentAsByteArray, BaseOutput::class.java)
                assertThat(response.errorMessage).isEqualTo(expectedMessage)
                assertThat(response.errorCode).isNotBlank()
                assertThat(result.response.getHeader("X-Trace-Id")).isNotBlank()
            }
    }
}
