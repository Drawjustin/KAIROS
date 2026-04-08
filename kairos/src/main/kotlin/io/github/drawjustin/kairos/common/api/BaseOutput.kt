package io.github.drawjustin.kairos.common.api

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

// 성공 응답에는 result만, 실패 응답에는 에러 정보만 보이도록 null 필드는 숨긴다.
@JsonInclude(JsonInclude.Include.NON_NULL)
// 모든 API 응답이 공통으로 가지는 에러 메타 필드만 부모로 분리한다.
open class BaseOutput(
    @field:Schema(description = "애플리케이션 에러 코드", example = "AUTH_003")
    open val errorCode: String? = null,
    @field:Schema(description = "클라이언트에 전달할 에러 메시지", example = "Invalid refresh token")
    open val errorMessage: String? = null,
)
