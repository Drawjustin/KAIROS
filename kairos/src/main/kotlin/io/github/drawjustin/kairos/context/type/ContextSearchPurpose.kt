package io.github.drawjustin.kairos.context.type

// context search를 어떤 용도로 호출했는지 남겨 이후 감사 로그/정책 분기에 활용한다.
enum class ContextSearchPurpose {
    CODE_ASSISTANT,
    INTERNAL_QA,
}
