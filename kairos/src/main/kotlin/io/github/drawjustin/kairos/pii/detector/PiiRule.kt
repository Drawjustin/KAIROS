package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType

// 민감정보 타입별 검출/마스킹 규칙. ProviderAdapter와 같이 구현체를 목록으로 주입받아 사용한다.
interface PiiRule {
    val type: PiiType

    fun detect(text: String): List<PiiMatch>

    fun mask(value: String): String
}
