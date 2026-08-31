package io.github.drawjustin.kairos.ai.provider

import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import java.util.function.Predicate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException

// provider 호출 실패를 "다시 걸어볼 가치가 있는가"로 나눈다.
//
// 5xx와 연결/타임아웃은 provider 쪽 일시적 문제이므로 재시도와 서킷 판정 대상이다.
// 4xx는 잘못된 API key나 잘못된 요청처럼 몇 번을 다시 걸어도 같은 답이 온다.
// 그런 요청을 재시도하면 복구는 안 되고 부하만 늘어난다.
internal fun Exception.toProviderFailure(): KairosException =
    when (this) {
        is KairosException -> this
        is HttpServerErrorException -> unavailable(message)
        is ResourceAccessException -> unavailable(message)
        is HttpClientErrorException -> rejected(message)
        else -> rejected(message)
    }

private fun unavailable(detail: String?): KairosException =
    KairosException(
        KairosErrorCode.AI_PROVIDER_UNAVAILABLE,
        detail ?: KairosErrorCode.AI_PROVIDER_UNAVAILABLE.message,
    )

private fun rejected(detail: String?): KairosException =
    KairosException(
        KairosErrorCode.AI_PROVIDER_ERROR,
        detail ?: KairosErrorCode.AI_PROVIDER_ERROR.message,
    )

// Resilience4j가 재시도와 서킷 집계에 쓸 판정 기준이다.
// 예외 타입만으로 나누면 4xx까지 함께 걸리므로 에러코드로 판정한다.
class RetryableProviderFailure : Predicate<Throwable> {
    override fun test(throwable: Throwable): Boolean =
        throwable is KairosException && throwable.errorCode == KairosErrorCode.AI_PROVIDER_UNAVAILABLE
}
