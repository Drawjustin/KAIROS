package io.github.drawjustin.kairos.common.api

// 모든 API 응답이 공통으로 가지는 에러 메타 필드만 부모로 분리한다.
open class BaseOutput(
    open val errorCode: String? = null,
    open val errorMessage: String? = null,
    open val slackError: Boolean? = null,
)
