package io.github.drawjustin.kairos.mcp.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.drawjustin.kairos.auth.security.AuthenticatedUser
import io.github.drawjustin.kairos.mcp.service.McpService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/mcp")
class McpController(
    private val mcpService: McpService,
    private val objectMapper: ObjectMapper,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handle(
        @AuthenticationPrincipal principal: AuthenticatedUser,
        @RequestBody body: JsonNode,
    ): ResponseEntity<JsonNode> {
        if (body.isArray) {
            val responses = objectMapper.createArrayNode()
            body.forEach { request ->
                mcpService.handle(principal, request)?.let(responses::add)
            }
            return if (responses.isEmpty) {
                ResponseEntity.status(HttpStatus.ACCEPTED).build()
            } else {
                ResponseEntity.ok(responses)
            }
        }

        return mcpService.handle(principal, body)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }
}
