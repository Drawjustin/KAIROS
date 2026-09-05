package io.github.drawjustin.kairos.observability

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

// 관리 포트가 서비스 포트와 같아지면 지표가 인증 없이 공개된다.
// 그 설정으로 기동되는 일이 없도록 막았는지 확인한다.
class ManagementPortSeparationCheckTests {
    private fun check(vararg properties: Pair<String, String>) =
        ManagementPortSeparationCheck(
            MockEnvironment().apply { properties.forEach { (key, value) -> setProperty(key, value) } },
        )

    @Test
    fun `refuses to start when the management port is not separated`() {
        val exception = assertThrows<IllegalStateException> {
            check("server.port" to "8080").afterPropertiesSet()
        }

        assertThat(exception.message).contains("management.server.port must differ")
    }

    @Test
    fun `refuses to start when the management port is left blank`() {
        assertThrows<IllegalStateException> {
            check("server.port" to "8080", "management.server.port" to "").afterPropertiesSet()
        }
    }

    @Test
    fun `refuses to start when the management port repeats the service port`() {
        assertThrows<IllegalStateException> {
            check("server.port" to "8080", "management.server.port" to "8080").afterPropertiesSet()
        }
    }

    @Test
    fun `starts when the ports are separated`() {
        assertThatCode {
            check("server.port" to "8080", "management.server.port" to "9090").afterPropertiesSet()
        }.doesNotThrowAnyException()
    }

    @Test
    fun `starts when the endpoints are turned off entirely`() {
        // -1은 관리 서버를 아예 띄우지 않겠다는 뜻이라 노출 위험이 없다.
        assertThatCode {
            check("server.port" to "8080", "management.server.port" to "-1").afterPropertiesSet()
        }.doesNotThrowAnyException()
    }
}
