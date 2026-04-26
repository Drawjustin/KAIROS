package io.github.drawjustin.kairos

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

// 기능별 통합 테스트가 공통 PostgreSQL 컨테이너 설정을 재사용하도록 돕는다.
abstract class IntegrationTestSupport {
    companion object {
        @JvmStatic
        // Spring ApplicationContext/Hikari pool과 생명주기가 엇갈리지 않도록 테스트 JVM 동안 직접 유지한다.
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        init {
            postgres.start()
        }

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
