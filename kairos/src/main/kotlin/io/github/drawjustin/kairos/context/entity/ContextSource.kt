package io.github.drawjustin.kairos.context.entity

import io.github.drawjustin.kairos.common.persistence.BaseEntity
import io.github.drawjustin.kairos.context.type.ContextSourceStatus
import io.github.drawjustin.kairos.context.type.ContextSourceType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "context_source")
@SQLDelete(sql = "update context_source set deleted_at = current_timestamp, updated_at = current_timestamp where id = ? and deleted_at is null")
@SQLRestriction("deleted_at is null")
// project가 참조할 수 있는 문서 저장소, URL, MCP 서버, 내부 도구 같은 context 출처다.
class ContextSource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var type: ContextSourceType,

    @Column(nullable = false, length = 120)
    var name: String,

    @Column(length = 500)
    var description: String? = null,

    @Column(length = 1000)
    var uri: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: ContextSourceStatus = ContextSourceStatus.ACTIVE,
) : BaseEntity()
