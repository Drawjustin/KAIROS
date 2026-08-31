package io.github.drawjustin.kairos.pii.detector

import io.github.drawjustin.kairos.pii.type.PiiType

// 정규식이 잡은 구간이 검증에서 탈락해도 그 안에 진짜 민감정보가 들어 있을 수 있다.
// "12 4539 1488 0343 6467"이 대표적인데, 앞의 12까지 함께 묶이면 Luhn 검증에 실패한다.
// 탈락한 구간을 통째로 건너뛰면 뒤따르는 카드번호를 놓치므로 시작 위치를 한 칸만 밀어 다시 찾는다.
// 민감정보 통제에서는 오탐보다 미탐이 위험하므로 탐색 비용을 조금 더 쓰는 쪽을 택했다.
internal fun Regex.findValidated(text: String, isValid: (MatchResult) -> Boolean): List<MatchResult> {
    val results = mutableListOf<MatchResult>()
    var searchFrom = 0
    while (searchFrom <= text.length) {
        val result = find(text, searchFrom) ?: break
        if (isValid(result)) {
            results += result
            searchFrom = result.range.last + 1
        } else {
            searchFrom = result.range.first + 1
        }
    }
    return results
}

internal fun MatchResult.toPiiMatch(type: PiiType): PiiMatch =
    PiiMatch(
        type = type,
        startIndex = range.first,
        endIndex = range.last + 1,
        value = value,
    )
