package io.github.drawjustin.kairos.common.persistence

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import java.time.Instant
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
// 모든 엔티티가 공통으로 가지는 생성/수정 시각을 한곳에서 관리한다.
abstract class BaseEntity {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    @Column(name = "deleted_at")
    // soft delete를 채택한 뒤에는 null 여부로 삭제 상태를 판단한다.
    var deletedAt: Instant? = null
}
