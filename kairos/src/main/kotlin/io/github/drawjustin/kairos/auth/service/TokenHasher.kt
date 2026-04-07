package io.github.drawjustin.kairos.auth.service

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.stereotype.Component

@Component
class TokenHasher {
    // refresh token 원문을 저장하지 않기 위해 고정 길이 해시로 바꾼다.
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
