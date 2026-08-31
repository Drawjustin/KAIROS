package io.github.drawjustin.kairos.pii.detector

import org.springframework.stereotype.Component

@Component
// 검출 구간을 타입별 규칙에 맞춰 가린 문자열을 만든다.
class PiiMasker(
    rules: List<PiiRule>,
) {
    private val rulesByType = rules.associateBy { it.type }

    fun mask(text: String, matches: List<PiiMatch>): String {
        if (matches.isEmpty()) {
            return text
        }
        val builder = StringBuilder(text.length)
        var cursor = 0
        matches.sortedBy { it.startIndex }
            .forEach { match ->
                if (match.startIndex < cursor) {
                    return@forEach
                }
                builder.append(text, cursor, match.startIndex)
                builder.append(rulesByType[match.type]?.mask(match.value) ?: match.value.fullMask())
                cursor = match.endIndex
            }
        builder.append(text, cursor, text.length)
        return builder.toString()
    }

    private fun String.fullMask(): String = MASK_CHARACTER.toString().repeat(length)
}
