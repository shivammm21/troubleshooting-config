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

    // ------------------------------------------------------------------
    // Docker / infrastructure
    // ------------------------------------------------------------------

    fun dockerStatus(): JsonNode = get("/api/docker/status")

    fun containers(): JsonNode = get("/api/containers")

    fun logs(containerId: String, lines: Int = 200): String =
        get("/api/containers/$containerId/logs?lines=$lines").path("logs").asText("")

    /** Feature 1: engine + container health + live TCP probes in one call. */
    fun infraHealth(): JsonNode = get("/api/infra/health")

    /** Feature 2: raw tail plus grouped failure signatures. */
    fun logAnalysis(containerId: String, lines: Int = 300): JsonNode =
        get("/api/containers/$containerId/log-analysis?lines=$lines")

    fun restartContainer(containerId: String): JsonNode =
        post("/api/containers/$containerId/restart", emptyMap())

    fun startContainer(containerId: String): JsonNode =
        post("/api/containers/$containerId/start", emptyMap())

    // ------------------------------------------------------------------
    // Project
    // ------------------------------------------------------------------

    fun attachProject(path: String): JsonNode =
        post("/api/projects", mapOf("path" to path))

    fun reindex(projectId: String): JsonNode =
        post("/api/projects/$projectId/index", emptyMap())

    fun projects(): JsonNode = get("/api/projects")

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    fun runDiagnostics(projectId: String?): JsonNode =
        post("/api/diagnostics", mapOf("projectId" to projectId))

    fun run(runId: String): JsonNode = get("/api/diagnostics/$runId")

    fun history(limit: Int = 20): JsonNode = get("/api/diagnostics?limit=$limit")

    fun verifications(limit: Int = 20): JsonNode = get("/api/verifications?limit=$limit")

    fun repairPreview(runId: String, findingId: String): JsonNode =
        post("/api/diagnostics/$runId/repair-preview", mapOf("findingId" to findingId))

    /**
     * Feature 5: the developer has approved this exact patch. [restart] also
     * restarts the affected containers so the change is actually loaded.
     */
    fun approveRepair(runId: String, findingId: String, restart: Boolean = true): JsonNode =
        post(
            "/api/diagnostics/$runId/repair-approval",
            mapOf("findingId" to findingId, "restart" to restart)
        )

    fun rollbackRepair(runId: String, findingId: String): JsonNode =
        post("/api/diagnostics/$runId/repair-rollback", mapOf("findingId" to findingId))

    fun verify(runId: String, findingId: String): JsonNode =
        post("/api/diagnostics/$runId/verify", mapOf("findingId" to findingId))

    /** Feature 6 report: Markdown text for the shareable diagnostic report. */
    fun report(runId: String): String = getText("/api/diagnostics/$runId/report")

    // ------------------------------------------------------------------
    // transport
    // ------------------------------------------------------------------

    private fun get(path: String): JsonNode = send(request(path).GET().build())

    private fun getText(path: String): String {
        val response = http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            throw IllegalStateException(errorMessage(response.body(), response.statusCode()))
        }
        return response.body() ?: ""
    }

    private fun post(path: String, body: Map<String, Any?>): JsonNode {
        val json = mapper.writeValueAsString(body)
        val builder = request(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
        return send(builder.build())
    }

    private fun request(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create("$baseUrl$path"))
            .timeout(Duration.ofSeconds(60))
            .header("X-StackPilot-Token", token)

    private fun send(request: HttpRequest): JsonNode {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            throw IllegalStateException(errorMessage(response.body(), response.statusCode()))
        }
        if (response.body().isNullOrBlank()) {
            return mapper.createObjectNode()
        }
        return mapper.readTree(response.body())
    }

    private fun errorMessage(body: String?, status: Int): String {
        if (body.isNullOrBlank()) {
            return "HTTP $status"
        }
        val parsed = try {
            mapper.readTree(body).path("error").asText("")
        } catch (_: Exception) {
            ""
        }
        return parsed.ifBlank { body }.ifBlank { "HTTP $status" }
    }
}
