package io.github.drawjustin.kairos.observability

import io.github.drawjustin.kairos.IntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// Spring Boot는 테스트에서 지표 내보내기를 기본으로 끈다. 운영과 같은 상태로 확인하려면 켜야 한다.
@AutoConfigureObservability
@TestPropertySource(properties = ["management.server.port=0"])
// 지표 엔드포인트가 서비스 포트로는 열리지 않는지 확인한다.
// 포트 분리가 Phase 5 망분리에서 "이 포트는 VPC 안에서만 연다"의 전제가 되므로
// 설정이 아니라 실제 응답으로 확인해 둔다.
class ManagementEndpointTests : IntegrationTestSupport() {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @LocalServerPort
    var servicePort: Int = 0

    @LocalManagementPort
    var managementPort: Int = 0

    private fun get(port: Int, path: String) =
        restTemplate.getForEntity("http://localhost:$port$path", String::class.java)

    @Test
    fun `serves the metrics endpoint on the management port only`() {
        assertThat(managementPort).isNotEqualTo(servicePort)

        val onManagementPort = get(managementPort, "/actuator/prometheus")

        assertThat(onManagementPort.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(onManagementPort.body).contains("jvm_memory_used_bytes")
    }

    @Test
    fun `does not expose the metrics endpoint on the service port`() {
        val onServicePort = get(servicePort, "/actuator/prometheus")

        assertThat(onServicePort.statusCode).isNotEqualTo(HttpStatus.OK)
    }

    @Test
    fun `keeps the endpoints that would reveal internal configuration closed`() {
        // env와 beans는 설정 값과 내부 구조를 그대로 보여주므로 노출 목록에 넣지 않았다.
        listOf("/actuator/env", "/actuator/beans", "/actuator/configprops").forEach { path ->
            // 닫힌 엔드포인트는 없는 경로다. 500으로 응답하면 취약점 스캐너가 훑고 지나가는 것만으로
            // 슬랙 알림이 쏟아지고 에러 로그가 스택트레이스로 덮인다.
            assertThat(get(managementPort, path).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @Test
    fun `answers an unknown path with not found instead of a server error`() {
        // 서비스 포트는 인증 계층이 먼저 막아 핸들러까지 가지 않는다.
        assertThat(get(servicePort, "/wp-admin/setup-config.php").statusCode)
            .isEqualTo(HttpStatus.FORBIDDEN)

        // 인증을 거치지 않는 관리 포트에서는 요청이 핸들러까지 도달한다.
        // 여기서 500으로 답하면 스캐너가 훑는 것만으로 슬랙 알림이 쏟아진다.
        val unknown = get(managementPort, "/actuator/does-not-exist")

        assertThat(unknown.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(unknown.body).contains("COMMON_002")
    }

    @Test
    fun `reports health without leaking component details`() {
        val health = get(managementPort, "/actuator/health")

        assertThat(health.statusCode).isEqualTo(HttpStatus.OK)
        // DB 접속 정보나 디스크 경로 같은 내부 사정이 응답에 실리면 안 된다.
        assertThat(health.body).doesNotContain("components")
    }
}
