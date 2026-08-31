package io.github.drawjustin.kairos.pii.type

// 검출된 민감정보를 어떻게 처리할지 결정한다. 어느 값이든 검출 사실 자체는 감사 로그에 남는다.
enum class PiiAction {
    // 원문 그대로 통과시킨다. 검출은 기록하되 차단하지 않는 관찰 모드로 쓴다.
    ALLOW,

    // 값을 마스킹한 뒤 통과시킨다.
    MASK,

    // 요청 자체를 거부한다.
    BLOCK,
    ;

    // BLOCK이 하나라도 있으면 요청 전체가 막히므로 정책 병합 시 가장 강한 값을 고른다.
    fun strongerOf(other: PiiAction): PiiAction = if (ordinal >= other.ordinal) this else other
}
