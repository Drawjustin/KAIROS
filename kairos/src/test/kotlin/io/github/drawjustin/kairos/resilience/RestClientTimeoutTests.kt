package io.github.drawjustin.kairos.resilience

import com.sun.net.httpserver.HttpServer
import io.github.drawjustin.kairos.ai.config.AiHttpProperties
import io.github.drawjustin.kairos.common.http.RestClientTimeoutConfig
import java.net.InetSocketAddress
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import org.springframework.web.client.ResourceAccessException

// 타임아웃이 실제로 걸리는지 확인한다.
// 설정 값만 검사하면 "설정했다"는 사실만 확인할 뿐 동작은 증명하지 못하므로
// 일부러 늦게 답하는 서버를 띄워 호출이 끊기는지 본다.
class RestClientTimeoutTests {
    private lateinit var server: HttpServer
    private var responseDelay: Duration = Duration.ZERO

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/slow") { exchange ->
            Thread.sleep(responseDelay.toMillis())
            val body = """{"ok":true}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun restClientWith(readTimeout: Duration): RestClient {
        val builder = RestClient.builder()
        RestClientTimeoutConfig()
            .restClientTimeoutCustomizer(
                AiHttpProperties(connectTimeout = Duration.ofSeconds(2), readTimeout = readTimeout),
            )
            .customize(builder)
        return builder.build()
    }

    private fun callSlowEndpoint(client: RestClient): String? =
        client.get()
            .uri("http://localhost:${server.address.port}/slow")
            .retrieve()
            .body(String::class.java)

    @Test
    fun `gives up on a provider that answers later than the read timeout`() {
        responseDelay = Duration.ofSeconds(3)
        val client = restClientWith(readTimeout = Duration.ofMillis(300))

        // 타임아웃이 없으면 이 호출은 3초를 기다리고, 실제 장애 상황에서는 영원히 기다린다.
        assertThatThrownBy { callSlowEndpoint(client) }
            .isInstanceOf(ResourceAccessException::class.java)
    }

    @Test
    fun `lets a response through when it arrives within the read timeout`() {
        responseDelay = Duration.ofMillis(50)
        val client = restClientWith(readTimeout = Duration.ofSeconds(5))

        assertThat(callSlowEndpoint(client)).contains("\"ok\":true")
    }
}
