package io.github.drawjustin.kairos.internaltool.controller

import io.github.drawjustin.kairos.internaltool.dto.MockDocSearchRequest
import io.github.drawjustin.kairos.internaltool.dto.MockDocSearchResponse
import io.github.drawjustin.kairos.internaltool.service.MockDocSearchService
import io.swagger.v3.oas.annotations.Hidden
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Hidden
@RestController
@RequestMapping("/internal/tools")
// 실제 MCP 서버를 분리하기 전까지 OpenAI tool calling loop를 검증하기 위한 내부 임시 API다.
class MockDocSearchController(
    private val mockDocSearchService: MockDocSearchService,
) {
    @PostMapping("/mock-doc-search")
    fun search(@Valid @RequestBody request: MockDocSearchRequest): MockDocSearchResponse =
        MockDocSearchResponse(
            documents = mockDocSearchService.search(request.query),
        )
}
