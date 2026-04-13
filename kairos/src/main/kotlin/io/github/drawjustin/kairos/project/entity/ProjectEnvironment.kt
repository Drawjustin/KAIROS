package io.github.drawjustin.kairos.project.entity

// 환경 구분이 있으면 같은 tenant 안에서도 운영/스테이징 사용량을 분리해 볼 수 있다.
enum class ProjectEnvironment {
    LOCAL,
    DEV,
    OPER,
}
