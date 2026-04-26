package io.github.drawjustin.kairos.tenant.type

// tenant는 활성/중지 상태만으로도 초기 운영 정책을 충분히 표현할 수 있다.
enum class TenantStatus {
    ACTIVE,
    SUSPENDED,
}
