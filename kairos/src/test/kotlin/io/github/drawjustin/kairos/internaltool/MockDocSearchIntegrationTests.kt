package io.github.drawjustin.kairos.internaltool

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.IntegrationTestSupport
import io.github.drawjustin.kairos.internaltool.dto.MockDocSearchRequest
import io.github.drawjustin.kairos.internaltool.dto.MockDocSearchResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MockDocSearchIntegrationTests : IntegrationTestSupport() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `mock doc search returns matching documents without authentication`() {
        val result = mockMvc.perform(
            post("/internal/tools/mock-doc-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(MockDocSearchRequest(query = "연차 정책"))),
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = objectMapper.readValue(result.response.contentAsByteArray, MockDocSearchResponse::class.java)
        assertThat(response.documents).isNotEmpty
        assertThat(response.documents.first().title).isEqualTo("휴가 및 연차 정책")
        assertThat(response.documents.first().content).contains("연차")
    }
}
