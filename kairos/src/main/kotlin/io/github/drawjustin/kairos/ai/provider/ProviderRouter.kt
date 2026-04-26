package io.github.drawjustin.kairos.ai.provider

import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.common.error.KairosErrorCode
import io.github.drawjustin.kairos.common.error.KairosException
import org.springframework.stereotype.Component

@Component
// 요청 모델을 기준으로 실제 provider adapter를 고른다.
class ProviderRouter(
    private val adapters: List<ProviderAdapter>,
) {
    fun route(model: AiModel): ProviderAdapter =
        adapters.firstOrNull { it.supports(model) }
            ?: throw KairosException(KairosErrorCode.AI_MODEL_NOT_SUPPORTED)
}
