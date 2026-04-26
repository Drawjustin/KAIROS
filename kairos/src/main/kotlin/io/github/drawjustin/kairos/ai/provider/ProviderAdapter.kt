package io.github.drawjustin.kairos.ai.provider

import io.github.drawjustin.kairos.ai.dto.ChatCompletionRequest
import io.github.drawjustin.kairos.ai.dto.ChatCompletionResponse
import io.github.drawjustin.kairos.ai.type.AiModel

// 공통 요청/응답을 기준으로 provider 구현을 갈아끼울 수 있게 하는 최소 인터페이스다.
interface ProviderAdapter {
    fun supports(model: AiModel): Boolean

    fun chatCompletion(request: ChatCompletionRequest): ChatCompletionResponse
}
