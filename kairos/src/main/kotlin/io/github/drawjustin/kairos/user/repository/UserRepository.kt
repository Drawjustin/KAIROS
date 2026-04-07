package io.github.drawjustin.kairos.user.repository

import io.github.drawjustin.kairos.user.entity.User
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

// 인증과 사용자 조회에 필요한 최소 쿼리만 정의한다.
interface UserRepository : JpaRepository<User, Long> {
    // 로그인과 중복 가입 검사에 모두 쓰는 이메일 조회다.
    fun findByEmail(email: String): Optional<User>

    // 전체 엔티티를 가져오지 않고도 가입 가능 여부를 빠르게 확인한다.
    fun existsByEmail(email: String): Boolean
}
