package io.github.drawjustin.kairos.common.error

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.common.slack.SlackNotifier
import jakarta.validation.ConstraintViolationException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
// 컨트롤러 전역에서 예외를 공통 BaseOutput 형식으로 변환한다.
class GlobalExceptionHandler(
    private val slackNotifier: SlackNotifier,
) {
    @ExceptionHandler(KairosException::class)
    fun handleKairosException(
        exception: KairosException,
        request: HttpServletRequest,
    ): ResponseEntity<BaseOutput> {
        val error = exception.errorCode
        notifySlackIfNeeded(error, exception, request)
        return ResponseEntity.status(error.status).body(
            BaseOutput(
                errorCode = error.code,
                errorMessage = exception.message,
            ),
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<BaseOutput> {
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

        notifySlackIfNeeded(error, exception, request)
        return ResponseEntity.status(error.status).body(
            BaseOutput(
                errorCode = error.code,
                errorMessage = message,
            ),
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    // @RequestParam, @PathVariable 등에서 발생하는 검증 오류를 처리한다.
    fun handleConstraintViolation(
        exception: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<BaseOutput> {
        val error = KairosErrorCode.INVALID_INPUT
        notifySlackIfNeeded(error, exception, request)
        return ResponseEntity.status(error.status).body(
            BaseOutput(
                errorCode = error.code,
                errorMessage = exception.message ?: error.message,
            ),
        )
    }

    @ExceptionHandler(Exception::class)
    // 위에서 분류하지 못한 예외도 응답 포맷이 깨지지 않게 마지막으로 잡아준다.
    fun handleException(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<BaseOutput> {
        val error = KairosErrorCode.INTERNAL_SERVER_ERROR
        notifySlackIfNeeded(error, exception, request)
        return ResponseEntity.status(error.status).body(
            BaseOutput(
                errorCode = error.code,
                errorMessage = error.message,
            ),
        )
    }

    private fun notifySlackIfNeeded(
        error: KairosErrorCode,
        exception: Exception,
        request: HttpServletRequest,
    ) {
        if (error.slackError) {
            slackNotifier.notifyError(error, exception, request)
        }
    }
}
