package io.github.drawjustin.kairos.ai.service

import io.github.drawjustin.kairos.context.type.ContextSearchPurpose
import io.github.drawjustin.kairos.project.entity.Project

// AI chat 흐름에서 tool 실행 감사 로그를 남기기 위한 호출 주체와 project 경계다.
data class AiToolExecutionContext(
    val userId: Long,
    val project: Project,
    val purpose: ContextSearchPurpose,
)
