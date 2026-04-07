package io.github.drawjustin.kairos.common.error

import io.github.drawjustin.kairos.common.api.BaseOutput
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
// 컨트롤러 전역에서 예외를 공통 BaseOutput 형식으로 변환한다.
class GlobalExceptionHandler {
    @ExceptionHandler(KairosException::class)
    fun handleKairosException(exception: KairosException): ResponseEntity<BaseOutput> {
        val error = exception.errorCode
        return ResponseEntity.status(error.status).body(
            BaseOutput(
                errorCode = error.code,
                errorMessage = exception.message,
                slackError = error.slackError,
            ),
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(exception: MethodArgumentNotValidException): ResponseEntity<BaseOutput> {
        val error = KairosErrorCode.INVALID_INPUT
        // @Valid 요청 바디의 필드 에러를 "field: message" 형식으로 합쳐 내려준다.
        val message = exception.bindingResult.allErrors
            .mapNotNull { validationError ->
                when (validationError) {
                    is FieldError -> "${validationError.field}: ${validationError.defaultMessage}"
                    else -> validationError.defaultMessage
                }
            }
            .joinToString(", ")
            .ifBlank { error.message }

        return ResponseEntity.status(error.status).body(
            BaseOutput(
                errorCode = error.code,
                errorMessage = message,
                slackError = error.slackError,
            ),
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    // @RequestParam, @PathVariable 등에서 발생하는 검증 오류를 처리한다.
    fun handleConstraintViolation(exception: ConstraintViolationException): ResponseEntity<BaseOutput> {
        val error = KairosErrorCode.INVALID_INPUT
        return ResponseEntity.status(error.status).body(
            BaseOutput(
                errorCode = error.code,
                errorMessage = exception.message ?: error.message,
                slackError = error.slackError,
            ),
        )
    }

    @ExceptionHandler(Exception::class)
    // 위에서 분류하지 못한 예외도 응답 포맷이 깨지지 않게 마지막으로 잡아준다.
    fun handleException(exception: Exception): ResponseEntity<BaseOutput> {
        val error = KairosErrorCode.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(error.status).body(
            BaseOutput(
                errorCode = error.code,
                errorMessage = error.message,
                slackError = error.slackError,
            ),
        )
    }
}
