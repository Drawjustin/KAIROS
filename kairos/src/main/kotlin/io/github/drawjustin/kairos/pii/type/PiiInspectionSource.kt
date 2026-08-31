package io.github.drawjustin.kairos.pii.type

// 민감정보를 어느 경로에서 검사했는지 구분한다.
// 세 경로 중 하나라도 빠지면 통제에 구멍이 생기므로 감사 로그에서 경로별로 확인할 수 있어야 한다.
enum class PiiInspectionSource {
    // 사용자가 보낸 프롬프트. 외부 provider로 그대로 나가는 본류다.
    CHAT_PROMPT,

    // 내부 문서 검색 결과. 사내 문서에 담긴 고객정보가 provider로 되돌아가는 경로다.
    TOOL_RESULT,

    // 개발 AI 도구가 보낸 검색 질의. 외부로 나가지는 않지만 감사 로그에 그대로 적재된다.
    CONTEXT_QUERY,
}
