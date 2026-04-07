package io.github.drawjustin.kairos.auth

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.auth.dto.AuthResponse
import io.github.drawjustin.kairos.auth.dto.LoginRequest
import io.github.drawjustin.kairos.auth.dto.LogoutRequest
import io.github.drawjustin.kairos.auth.dto.MeResponse
import io.github.drawjustin.kairos.auth.dto.RefreshRequest
import io.github.drawjustin.kairos.auth.dto.RegisterRequest
import io.github.drawjustin.kairos.auth.repository.RefreshSessionRepository
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
        refreshSessionRepository.deleteAll()
        userRepository.deleteAll()
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
        assertThat(userRepository.findByEmail("tester@example.com")).isPresent
        assertThat(refreshSessionRepository.findBySessionId(response.sessionId)).isNotNull
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
        assertThat(response.email).isEqualTo("me@example.com")
        assertThat(response.role).isEqualTo("USER")
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
            .andExpect(status().isNoContent)

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
    }

    private fun register(request: RegisterRequest): AuthResponse {
        // 테스트 본문에서는 핵심 시나리오만 보이도록 HTTP 호출 세부는 헬퍼로 감싼다.
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsByteArray, AuthResponse::class.java)
    }

    private fun login(request: LoginRequest): AuthResponse {
        // 로그인 성공 응답을 객체로 바로 역직렬화해 테스트 가독성을 높인다.
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsByteArray, AuthResponse::class.java)
    }

    private fun refresh(request: RefreshRequest): AuthResponse {
        // refresh 재발급 시나리오도 동일한 방식으로 재사용 가능하게 뽑아둔다.
        val result = mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)),
        )
            .andExpect(status().isOk)
            .andReturn()

        return objectMapper.readValue(result.response.contentAsByteArray, AuthResponse::class.java)
    }

    private fun ResultActions.assertUnauthorized(expectedMessage: String) {
        // 현재 에러 응답은 JSON body가 아니라 status + errorMessage 중심으로 검증한다.
        andExpect(status().isUnauthorized)
            .andExpect { result ->
                assertThat(result.response.errorMessage).isEqualTo(expectedMessage)
            }
    }
}
