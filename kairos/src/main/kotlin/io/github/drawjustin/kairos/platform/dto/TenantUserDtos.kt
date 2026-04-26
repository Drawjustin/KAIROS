package io.github.drawjustin.kairos.platform.dto

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.tenant.type.TenantUserRole
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateTenantUserRequest(
    @field:Schema(description = "tenant에 추가할 사용자 이메일", example = "member@example.com")
    @field:NotBlank
    @field:Size(max = 255)
    val email: String,
    @field:Schema(description = "tenant 안에서 부여할 역할", example = "ADMIN")
    @field:NotNull
    val role: TenantUserRole,
)

data class UpdateTenantUserRoleRequest(
    @field:Schema(description = "변경할 tenant 역할", example = "MEMBER")
    @field:NotNull
    val role: TenantUserRole,
)

data class TenantUserOutput(
    @field:Schema(description = "tenant_user ID", example = "1")
    val id: Long,
    @field:Schema(description = "소속 tenant ID", example = "1")
    val tenantId: Long,
    @field:Schema(description = "소속 user ID", example = "3")
    val userId: Long,
    @field:Schema(description = "사용자 이메일", example = "member@example.com")
    val email: String,
    @field:Schema(description = "tenant 역할", example = "ADMIN")
    val role: TenantUserRole,
    @field:Schema(description = "생성 시각", example = "2026-04-13T10:02:00Z")
    val createdAt: Instant,
)

data class TenantUserResponse(
    @field:Schema(description = "생성 또는 수정된 tenant 사용자 정보")
    val result: TenantUserOutput,
) : BaseOutput()

data class TenantUsersResponse(
    @field:Schema(description = "조회된 tenant 사용자 목록")
    val result: List<TenantUserOutput>,
) : BaseOutput()
