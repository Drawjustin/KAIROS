package io.github.drawjustin.kairos.resilience

import com.sun.net.httpserver.HttpServer
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.provider.ProviderRouter
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "kairos.ai.openai.api-key=test-openai-key",
        "resilience4j.retry.instances.openai.max-attempts=3",
        "resilience4j.retry.instances.openai.wait-duration=10ms",
        "resilience4j.circuitbreaker.instances.openai.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.openai.minimum-number-of-calls=4",
        "resilience4j.circuitbreaker.instances.openai.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.openai.wait-duration-in-open-state=10s",
        "resilience4j.circuitbreaker.instances.openai.automatic-transition-from-open-to-half-open-enabled=false",
    ],
)
// 재시도와 서킷이 "설정되어 있다"가 아니라 실제로 그렇게 동작하는지 확인한다.
// provider 자리에 응답 코드를 바꿔 낼 수 있는 서버를 놓고 호출 횟수를 센다.
class ProviderCircuitBreakerTests : IntegrationTestSupport() {
    @Autowired
    lateinit var providerRouter: ProviderRouter

    @Autowired
    lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @BeforeEach
    fun reset() {
        attempts.set(0)
        circuitBreakerRegistry.circuitBreaker("openai").reset()
    }

    private fun callOpenAi(): Throwable? =
        runCatching {
            providerRouter.route(AiModel.GPT_4O_MINI).chatCompletion(
                ChatCompletionRequest(
                    model = AiModel.GPT_4O_MINI,
                    messages = listOf(ChatMessageRequest(role = ChatRole.USER, content = "안녕")),
                ),
                emptyList(),
                null,
            )
        }.exceptionOrNull()

    @Test
    fun `retries a server error up to the configured attempts`() {
        responseStatus = 500

        val failure = callOpenAi()

        assertThat(failure).isInstanceOf(KairosException::class.java)
        assertThat((failure as KairosException).errorCode).isEqualTo(KairosErrorCode.AI_PROVIDER_UNAVAILABLE)
        assertThat(attempts.get()).isEqualTo(3)
    }

    @Test
    fun `never retries a client error`() {
        responseStatus = 401

        val failure = callOpenAi()

        assertThat(failure).isInstanceOf(KairosException::class.java)
        // 잘못된 API key를 세 번 더 보낸다고 답이 달라지지 않는다. 복구는 없고 부하만 늘어난다.
        assertThat((failure as KairosException).errorCode).isEqualTo(KairosErrorCode.AI_PROVIDER_ERROR)
        assertThat(attempts.get()).isEqualTo(1)
    }

    @Test
    fun `opens the circuit after enough server errors and then fails fast`() {
        responseStatus = 500
        val breaker = circuitBreakerRegistry.circuitBreaker("openai")

        repeat(4) { callOpenAi() }

        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.OPEN)

        val callsBeforeFailFast = attempts.get()
        val failure = callOpenAi()

        assertThat(failure).isInstanceOf(CallNotPermittedException::class.java)
        // 서킷이 열린 뒤에는 provider를 아예 건드리지 않는다.
        assertThat(attempts.get()).isEqualTo(callsBeforeFailFast)
    }

    @Test
    fun `keeps the circuit closed when only client errors happen`() {
        responseStatus = 400
        val breaker = circuitBreakerRegistry.circuitBreaker("openai")

        repeat(6) { callOpenAi() }

        // provider는 멀쩡한데 우리 요청이 잘못된 상황이다. 여기서 서킷을 열면 스스로 길을 막는 셈이다.
        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
    }

    companion object {
        @JvmStatic
        var responseStatus: Int = 500

        @JvmStatic
        val attempts = AtomicInteger()

        @JvmStatic
        val flakyProvider: HttpServer = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/") { exchange ->
                attempts.incrementAndGet()
                val body = """{"error":"forced"}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(responseStatus, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun providerEndpoint(registry: DynamicPropertyRegistry) {
            registry.add("kairos.ai.openai.base-url") { "http://localhost:${flakyProvider.address.port}" }
        }
    }
}
