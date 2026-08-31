package io.github.drawjustin.kairos.resilience

import com.sun.net.httpserver.HttpServer
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatMessageRequest
import io.github.drawjustin.kairos.ai.provider.ProviderRouter
import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.ai.type.ChatRole
import io.github.resilience4j.bulkhead.BulkheadFullException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
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
        "resilience4j.bulkhead.instances.openai.max-concurrent-calls=2",
        "resilience4j.bulkhead.instances.openai.max-wait-duration=0",
        "resilience4j.bulkhead.instances.gemini.max-concurrent-calls=2",
        "resilience4j.bulkhead.instances.gemini.max-wait-duration=0",
        "kairos.ai.openai.api-key=test-openai-key",
        "kairos.ai.gemini.api-key=test-gemini-key",
    ],
)
// provider 하나가 느려졌을 때 그 지연이 다른 provider까지 멈추게 하지 않는지 확인한다.
// 응답을 일부러 늦게 주는 서버를 provider 자리에 놓고 동시 호출을 밀어 넣는다.
class ProviderBulkheadTests : IntegrationTestSupport() {
    @Autowired
    lateinit var providerRouter: ProviderRouter

    private fun chatRequest(model: AiModel) = ChatCompletionRequest(
        model = model,
        messages = listOf(ChatMessageRequest(role = ChatRole.USER, content = "안녕")),
    )

    private fun callProvider(model: AiModel): Throwable? =
        runCatching { providerRouter.route(model).chatCompletion(chatRequest(model), emptyList(), null) }
            .exceptionOrNull()

    @Test
    fun `rejects calls beyond the concurrency limit instead of queueing them`() {
        val concurrency = 6
        val permits = 2
        val ready = CountDownLatch(concurrency)
        val start = CountDownLatch(1)
        val rejected = AtomicInteger()
        val admitted = AtomicInteger()

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            repeat(concurrency) {
                executor.submit {
                    ready.countDown()
                    start.await()
                    val failure = callProvider(AiModel.GPT_4O_MINI)
                    if (failure is BulkheadFullException) {
                        rejected.incrementAndGet()
                    } else {
                        admitted.incrementAndGet()
                    }
                }
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
            start.countDown()
        }

        // 대기 없이 즉시 거절해야 스레드가 붙잡히지 않는다.
        assertThat(admitted.get()).isEqualTo(permits)
        assertThat(rejected.get()).isEqualTo(concurrency - permits)
    }

    @Test
    fun `keeps one provider's congestion from spilling into another`() {
        val start = CountDownLatch(1)
        val openAiCallsStarted = CountDownLatch(2)
        val geminiFailure = AtomicInteger(-1)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            // OpenAI 쪽 자리를 모두 채운다.
            repeat(2) {
                executor.submit {
                    openAiCallsStarted.countDown()
                    callProvider(AiModel.GPT_4O_MINI)
                }
            }
            executor.submit {
                openAiCallsStarted.await()
                start.await()
                // OpenAI가 포화 상태여도 Gemini 호출은 자기 자리를 그대로 쓴다.
                val failure = callProvider(AiModel.GEMINI_2_5_FLASH)
                geminiFailure.set(if (failure is BulkheadFullException) 1 else 0)
            }
            assertThat(openAiCallsStarted.await(10, TimeUnit.SECONDS)).isTrue()
            start.countDown()
        }

        assertThat(geminiFailure.get()).isEqualTo(0)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun providerEndpoints(registry: DynamicPropertyRegistry) {
            registry.add("kairos.ai.openai.base-url") { "http://localhost:${slowProvider.address.port}" }
            registry.add("kairos.ai.gemini.base-url") { "http://localhost:${slowProvider.address.port}" }
        }

        @JvmStatic
        // provider가 느리게 응답하는 상황을 재현한다. 응답 내용은 중요하지 않고 붙잡혀 있는 시간이 중요하다.
        val slowProvider: HttpServer = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/") { exchange ->
                Thread.sleep(1_500)
                val body = "{}".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    }
}
