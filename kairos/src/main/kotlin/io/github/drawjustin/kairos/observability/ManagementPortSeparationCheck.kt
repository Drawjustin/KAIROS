package io.github.drawjustin.kairos.observability

import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
// 관리 엔드포인트를 여는 보안 규칙은 "이 포트는 외부에 노출하지 않는다"를 전제로 인증을 걸지 않는다.
// 그래서 포트가 서비스 포트와 같아지는 순간 지표가 인증 없이 공개된다.
// 설정 한 줄이 빠졌을 뿐인데 조용히 열리는 상황을 막기 위해, 그런 설정으로는 아예 기동하지 않는다.
class ManagementPortSeparationCheck(
    private val environment: Environment,
) : InitializingBean {
    override fun afterPropertiesSet() {
        if (ManagementPortType.get(environment) == ManagementPortType.SAME) {
            throw IllegalStateException(
                "management.server.port must differ from server.port. " +
                    "Actuator endpoints are served without authentication and rely on the port itself " +
                    "as the boundary, so sharing the service port would expose them publicly. " +
                    "Set a separate port, or set management.server.port=-1 to turn the endpoints off.",
            )
        }
    }
}
