package io.github.drawjustin.kairos.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users")
// 현재는 인증의 주체 역할을 하는 가장 단순한 사용자 엔티티다.
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    // 로그인 식별자로 쓰이므로 유니크 제약을 둔다.
    var email: String,

    @Column(nullable = false)
    // 평문이 아닌 bcrypt 해시가 저장된다.
    var password: String,

    @Column(nullable = false)
    // 이후 권한 확장 시 관리자/사용자 구분의 기준이 된다.
    var role: String = "USER",

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
