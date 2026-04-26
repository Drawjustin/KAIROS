package io.github.drawjustin.kairos.platform.dto

import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.tenant.type.TenantStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateTenantRequest(
    @field:Schema(description = "tenant 이름", example = "search-team")
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
)

data class TenantOutput(
    @field:Schema(description = "tenant ID", example = "1")
    val id: Long,
    @field:Schema(description = "tenant 이름", example = "search-team")
    val name: String,
    @field:Schema(description = "tenant 상태", example = "ACTIVE")
    val status: TenantStatus,
    @field:Schema(description = "생성 시각", example = "2026-04-13T10:00:00Z")
    val createdAt: Instant,
)

data class TenantResponse(
    @field:Schema(description = "생성된 tenant 정보")
    val result: TenantOutput,
) : BaseOutput()

data class TenantsResponse(
    @field:Schema(description = "조회된 tenant 목록")
    val result: List<TenantOutput>,
) : BaseOutput()
