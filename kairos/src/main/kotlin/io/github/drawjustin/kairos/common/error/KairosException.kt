package io.github.drawjustin.kairos.common.error

// 서비스 계층에서 의미 있는 에러코드를 담아 던지기 위한 공통 예외다.
class KairosException(
    val errorCode: KairosErrorCode,
    // 기본 메시지는 enum에 정의된 값을 따르되, 필요하면 개별 메시지로 덮어쓸 수 있다.
    override val message: String = errorCode.message,
) : RuntimeException(message)
