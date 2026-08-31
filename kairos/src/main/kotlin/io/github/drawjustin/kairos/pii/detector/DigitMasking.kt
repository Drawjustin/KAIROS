package io.github.drawjustin.kairos.pii.detector

// 하이픈/공백 같은 구분자는 그대로 두고 숫자만 가려야 사람이 읽었을 때 형식을 알아볼 수 있다.
internal fun maskDigits(value: String, keepHead: Int, keepTail: Int): String {
    val digitCount = value.count(Char::isDigit)
    var seen = 0
    return buildString(value.length) {
        for (character in value) {
            if (!character.isDigit()) {
                append(character)
                continue
            }
            val keep = seen < keepHead || seen >= digitCount - keepTail
            append(if (keep) character else MASK_CHARACTER)
            seen++
        }
    }
}

internal const val MASK_CHARACTER = '*'
