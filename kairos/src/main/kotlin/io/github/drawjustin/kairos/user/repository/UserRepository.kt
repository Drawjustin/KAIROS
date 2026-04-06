package io.github.drawjustin.kairos.user.repository

import io.github.drawjustin.kairos.user.entity.User
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>

    fun existsByEmail(email: String): Boolean
}
