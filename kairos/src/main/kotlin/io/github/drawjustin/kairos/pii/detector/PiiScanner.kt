package io.github.drawjustin.kairos.pii.detector

import org.springframework.stereotype.Component

@Component
// 등록된 모든 규칙을 돌려 검출 결과를 하나로 합친다.
class PiiScanner(
    private val rules: List<PiiRule>,
) {
    fun scan(text: String): List<PiiMatch> {
        if (text.isBlank()) {
            return emptyList()
        }
        return rules.flatMap { it.detect(text) }.resolveOverlaps()
    }

    // 같은 구간을 여러 규칙이 잡을 수 있다. 주민번호 13자리가 카드번호 패턴에도 걸리는 식이다.
    // 더 긴 매치를 남겨야 정보가 더 많이 담긴 쪽을 기준으로 마스킹된다.
    private fun List<PiiMatch>.resolveOverlaps(): List<PiiMatch> {
        val accepted = mutableListOf<PiiMatch>()
        var lastEndIndex = 0
        sortedWith(compareBy<PiiMatch> { it.startIndex }.thenByDescending { it.length })
            .forEach { match ->
                if (match.startIndex >= lastEndIndex) {
                    accepted += match
                    lastEndIndex = match.endIndex
                }
            }
        return accepted
    }
}
