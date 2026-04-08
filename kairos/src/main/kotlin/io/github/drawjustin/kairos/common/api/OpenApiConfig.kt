package io.github.drawjustin.kairos.common.api

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
// Swagger UI와 /v3/api-docs에 노출될 기본 문서 정보를 정의한다.
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Kairos API")
                    .description("JWT 기반 인증과 사용자 기능을 제공하는 Kairos 백엔드 API")
                    .version("v1")
                    .contact(
                        Contact()
                            .name("Kairos")
                    ),
            )
            .components(
                Components().addSecuritySchemes(
                    "bearerAuth",
                    SecurityScheme()
                        .name("Authorization")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
}
