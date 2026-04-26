package io.github.drawjustin.kairos.platform.dto

import io.github.drawjustin.kairos.ai.type.AiModel
import io.github.drawjustin.kairos.common.api.BaseOutput
import io.github.drawjustin.kairos.project.type.ProjectEnvironment
import io.github.drawjustin.kairos.project.type.ProjectStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateProjectRequest(
    @field:Schema(description = "project 이름", example = "chatbot-prod")
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
    @field:Schema(description = "project 환경", example = "OPER")
    val environment: ProjectEnvironment = ProjectEnvironment.OPER,
)

data class ProjectOutput(
    @field:Schema(description = "project ID", example = "1")
    val id: Long,
    @field:Schema(description = "소속 tenant ID", example = "1")
    val tenantId: Long,
    @field:Schema(description = "project 이름", example = "chatbot-prod")
    val name: String,
    @field:Schema(description = "환경", example = "PRODUCTION")
    val environment: ProjectEnvironment,
    @field:Schema(description = "상태", example = "ACTIVE")
    val status: ProjectStatus,
    @field:Schema(description = "생성 시각", example = "2026-04-13T10:05:00Z")
    val createdAt: Instant,
)

data class ProjectsResponse(
    @field:Schema(description = "조회된 project 목록")
    val result: List<ProjectOutput>,
) : BaseOutput()

data class ProjectResponse(
    @field:Schema(description = "생성된 project 정보")
    val result: ProjectOutput,
) : BaseOutput()

data class UpdateProjectAllowedModelsRequest(
    @field:Schema(description = "project에서 호출을 허용할 모델 목록", example = """["gpt-4o-mini","gemini-2.5-flash"]""")
    @field:NotEmpty
    val models: List<AiModel>,
)

data class ProjectAllowedModelsOutput(
    @field:Schema(description = "project ID", example = "1")
    val projectId: Long,
    @field:Schema(description = "호출 허용 모델 목록")
    val models: List<AiModel>,
)

data class ProjectAllowedModelsResponse(
    @field:Schema(description = "project 모델 허용 정책")
    val result: ProjectAllowedModelsOutput,
) : BaseOutput()
