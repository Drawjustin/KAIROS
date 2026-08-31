package io.github.drawjustin.kairos.ai.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

// 외부 HTTP 호출의 타임아웃이다.
// 타임아웃이 없으면 provider가 응답하지 않을 때 스레드가 영원히 붙잡히고,
// 무엇보다 실패로 판정되는 시점이 없어 서킷 브레이커가 열리지 않는다.
@ConfigurationProperties(prefix = "kairos.http")
data class AiHttpProperties(
    // 연결 자체가 안 되는 상황은 빠르게 포기한다.
    var connectTimeout: Duration = Duration.ofSeconds(2),
    // 생성 모델은 응답이 느릴 수 있어 읽기 타임아웃은 넉넉히 둔다.
    var readTimeout: Duration = Duration.ofSeconds(30),
)
