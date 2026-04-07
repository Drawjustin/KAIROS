package io.github.drawjustin.kairos

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class KairosApplicationTests : IntegrationTestSupport() {

	@Test
	// 가장 얇은 스모크 테스트로 전체 애플리케이션 부팅 가능 여부만 확인한다.
	fun contextLoads() {
	}

}
