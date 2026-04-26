package io.github.drawjustin.kairos.internaltool.dto

import jakarta.validation.constraints.NotBlank

data class MockDocSearchRequest(
    @field:NotBlank
    val query: String,
)

data class MockDocSearchResponse(
    val documents: List<MockDocSearchDocument>,
)

data class MockDocSearchDocument(
    val title: String,
    val content: String,
    val source: String,
    val score: Int,
)
