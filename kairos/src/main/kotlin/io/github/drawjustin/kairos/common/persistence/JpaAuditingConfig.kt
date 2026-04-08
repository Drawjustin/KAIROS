package io.github.drawjustin.kairos.common.persistence

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaAuditing
// createdAt/updatedAt을 persist/update 시점에 자동으로 채운다.
class JpaAuditingConfig
