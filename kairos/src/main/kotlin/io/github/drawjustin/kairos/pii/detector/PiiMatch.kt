package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType

// 검출된 민감정보 한 건. value는 마스킹 문자열을 만들 때만 쓰고 어떤 경로로도 저장하지 않는다.
data class PiiMatch(
    val type: PiiType,
    val startIndex: Int,
    val endIndex: Int,
    val value: String,
) {
    val length: Int get() = endIndex - startIndex
}
