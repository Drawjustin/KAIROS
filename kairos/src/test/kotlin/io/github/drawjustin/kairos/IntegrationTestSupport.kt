package io.github.drawjustin.kairos

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
// 기능별 통합 테스트가 공통 PostgreSQL 컨테이너 설정을 재사용하도록 돕는다.
abstract class IntegrationTestSupport {
    companion object {
        @Container
        @JvmStatic
        // 기능별 통합 테스트가 모두 같은 PostgreSQL 테스트 환경을 공유한다.
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

    @JvmStatic
    @DynamicPropertySource
    // SpringBootTest가 뜰 때 datasource를 컨테이너 DB로 연결한다.
    fun configureProperties(registry: DynamicPropertyRegistry) {
        // 컨테이너가 띄운 접속 정보를 Spring datasource로 주입한다.
        registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName)
        }
    }
}
