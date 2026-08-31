package io.github.drawjustin.kairos.pii.type

// 검출 대상 민감정보 종류. project별 정책과 감사 로그가 모두 이 값을 기준으로 관리된다.
enum class PiiType(
    val displayName: String,
    // 오탐 위험이 큰 타입은 명시적으로 켜야만 동작하도록 기본값을 다르게 둔다.
    val defaultAction: PiiAction,
) {
    RESIDENT_REGISTRATION_NUMBER("주민등록번호", PiiAction.BLOCK),
    FOREIGNER_REGISTRATION_NUMBER("외국인등록번호", PiiAction.BLOCK),
    CREDIT_CARD_NUMBER("카드번호", PiiAction.BLOCK),
    BANK_ACCOUNT_NUMBER("계좌번호", PiiAction.MASK),
    PHONE_NUMBER("휴대전화번호", PiiAction.MASK),
    EMAIL("이메일", PiiAction.MASK),

    // 영문 1자 + 숫자 조합이라 제품 코드/일련번호와 겹치기 쉬워 기본값을 ALLOW로 둔다.
    PASSPORT_NUMBER("여권번호", PiiAction.ALLOW),
}
