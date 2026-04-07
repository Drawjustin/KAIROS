package io.github.drawjustin.kairos.common.slack

import io.github.drawjustin.kairos.common.error.KairosErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
// slackError=true인 예외를 지정된 웹훅 채널로 전달한다.
class SlackNotifier(
    private val slackProperties: SlackProperties,
    restClientBuilder: RestClient.Builder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilder.build()

    fun notifyError(error: KairosErrorCode, exception: Exception, request: HttpServletRequest?) {
        val webhookUrl = slackProperties.webhookUrl.trim()
        if (webhookUrl.isBlank()) {
            return
        }

        val payload = SlackWebhookRequest(
            text = buildMessage(error, exception, request),
        )

        try {
            restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        } catch (sendException: Exception) {
            // 슬랙 전송 실패가 원래 API 응답까지 깨뜨리면 안 되므로 로그만 남긴다.
            logger.error("Failed to send Slack notification for error code {}", error.code, sendException)
        }
    }

    private fun buildMessage(
        error: KairosErrorCode,
        exception: Exception,
        request: HttpServletRequest?,
    ): String = buildString {
        appendLine("[Kairos Error Alert]")
        appendLine("code: ${error.code}")
        appendLine("status: ${error.status.value()}")
        appendLine("message: ${exception.message ?: error.message}")
        appendLine("exception: ${exception::class.simpleName ?: exception.javaClass.name}")

        request?.let {
            appendLine("request: ${it.method} ${it.requestURI}")
            appendLine("clientIp: ${it.remoteAddr}")
        }
    }.trim()

    private data class SlackWebhookRequest(
        val text: String,
    )
}
