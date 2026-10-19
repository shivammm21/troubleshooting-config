package com.stackpilot.desktop

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class BackendClient(
    private val baseUrl: String,
    private val token: String
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun dockerStatus(): JsonNode = get("/api/docker/status")

    fun containers(): JsonNode = get("/api/containers")

    fun logs(containerId: String, lines: Int = 200): String =
        get("/api/containers/$containerId/logs?lines=$lines").path("logs").asText("")

    fun attachProject(path: String): JsonNode =
        post("/api/projects", mapOf("path" to path))

    fun runDiagnostics(projectId: String?): JsonNode =
        post("/api/diagnostics", mapOf("projectId" to projectId))

    fun repairPreview(runId: String, findingId: String): JsonNode =
        post("/api/diagnostics/$runId/repair-preview", mapOf("findingId" to findingId))

    fun approveRepair(runId: String, findingId: String): JsonNode =
        post("/api/diagnostics/$runId/repair-approval", mapOf("findingId" to findingId))

    fun verify(runId: String, findingId: String): JsonNode =
        post("/api/diagnostics/$runId/verify", mapOf("findingId" to findingId))

    private fun get(path: String): JsonNode {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl$path"))
            .timeout(Duration.ofSeconds(20))
            .header("X-StackPilot-Token", token)
            .GET()
            .build()
        return send(request)
    }

    private fun post(path: String, body: Map<String, String?>): JsonNode {
        val json = mapper.writeValueAsString(body)
        val request = HttpRequest.newBuilder(URI.create("$baseUrl$path"))
            .timeout(Duration.ofSeconds(30))
            .header("X-StackPilot-Token", token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        return send(request)
    }

    private fun send(request: HttpRequest): JsonNode {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            val message = try {
                mapper.readTree(response.body()).path("error").asText(response.body())
            } catch (_: Exception) {
                response.body()
            }
            throw IllegalStateException(message.ifBlank { "HTTP ${response.statusCode()}" })
        }
        if (response.body().isNullOrBlank()) {
            return mapper.createObjectNode()
        }
        return mapper.readTree(response.body())
    }
}
