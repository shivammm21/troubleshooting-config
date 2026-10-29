package com.stackpilot.desktop

import com.fasterxml.jackson.databind.JsonNode
import com.stackpilot.desktop.model.ContainerItem
import com.stackpilot.desktop.model.DependencyItem
import com.stackpilot.desktop.model.DiagnosticFinding
import com.stackpilot.desktop.model.EvidenceItem
import com.stackpilot.desktop.model.FilePatchItem
import com.stackpilot.desktop.model.InfraHealth
import com.stackpilot.desktop.model.LogAnalysis
import com.stackpilot.desktop.model.RepairActionItem
import com.stackpilot.desktop.model.RepairOutcome
import com.stackpilot.desktop.model.RunHistoryItem
import com.stackpilot.desktop.model.SignatureItem
import com.stackpilot.desktop.model.VerificationOutcome
import com.stackpilot.desktop.model.VerificationRecordItem

/**
 * Translates backend JSON into the UI models.
 *
 * <p>Every parser is defensive: a missing or renamed field degrades to a safe
 * default instead of crashing the dashboard, because diagnostics must keep
 * working while the stack is broken.
 */
object ApiParsers {

    fun containers(node: JsonNode): List<ContainerItem> {
        if (!node.isArray) return emptyList()
        return node.mapNotNull { it.toContainer() }
    }

    fun container(node: JsonNode): ContainerItem? = node.toContainer()

    fun health(node: JsonNode): InfraHealth {
        if (node.isMissingNode || node.isNull) return InfraHealth()
        val containers = containers(node.path("containers"))
        return InfraHealth(
            overall = node.path("overall").asText("UNKNOWN"),
            dockerConnected = node.path("dockerConnected").asBoolean(false),
            engineName = node.path("engineName").asText("Docker Engine"),
            dockerError = node.path("dockerError").takeIf { it.isTextual }?.asText(),
            total = node.path("totalContainers").asInt(containers.size),
            running = node.path("runningContainers").asInt(0),
            unhealthy = node.path("unhealthyContainers").asInt(0),
            stopped = node.path("stoppedContainers").asInt(0),
            containers = containers,
            dependencies = dependencies(node.path("dependencies")),
            issues = strings(node.path("issues")),
            checkedAt = node.path("checkedAt").asText("")
        )
    }

    fun logAnalysis(node: JsonNode): LogAnalysis {
        val summary = node.path("summary")
        val signatures = if (summary.path("signatures").isArray) {
            summary.path("signatures").map { signature ->
                SignatureItem(
                    signature = signature.path("signature").asText(""),
                    category = signature.path("category").asText("OTHER"),
                    severity = signature.path("severity").asText("WARN"),
                    count = signature.path("count").asInt(0),
                    sample = signature.path("sample").asText(""),
                    line = signature.path("line").asInt(0)
                )
            }
        } else {
            emptyList()
        }
        return LogAnalysis(
            containerId = node.path("containerId").asText(""),
            containerName = node.path("containerName").asText(""),
            totalLines = summary.path("totalLines").asInt(0),
            errorLines = summary.path("errorLines").asInt(0),
            warnLines = summary.path("warnLines").asInt(0),
            infoLines = summary.path("infoLines").asInt(0),
            categories = signatures.map { it.category }.distinct(),
            signatures = signatures,
            exceptionTypes = strings(summary.path("exceptionTypes"))
        )
    }

    fun findings(node: JsonNode): List<DiagnosticFinding> {
        if (!node.isArray) return emptyList()
        return node.mapNotNull { it.toFinding() }
    }

    fun runHistory(node: JsonNode): List<RunHistoryItem> {
        if (!node.isArray) return emptyList()
        return node.map { run ->
            RunHistoryItem(
                id = run.path("id").asText(""),
                createdAt = run.path("createdAt").asText(""),
                projectId = run.path("projectId").takeIf { it.isTextual }?.asText(),
                findings = run.path("findings").asInt(0),
                errors = run.path("errors").asInt(0),
                repairable = run.path("repairable").asInt(0)
            )
        }
    }

    fun verifications(node: JsonNode): List<VerificationRecordItem> {
        if (!node.isArray) return emptyList()
        return node.map { record ->
            VerificationRecordItem(
                id = record.path("id").asText(""),
                at = record.path("at").asText(""),
                runId = record.path("runId").asText(""),
                ruleId = record.path("ruleId").asText(""),
                status = record.path("status").asText("UNKNOWN"),
                resolved = record.path("resolved").asBoolean(false),
                summary = record.path("summary").asText("")
            )
        }
    }

    fun repairOutcome(node: JsonNode): RepairOutcome = RepairOutcome(
        applied = node.path("applied").asBoolean(false),
        changedFiles = strings(node.path("changedFiles")),
        backups = strings(node.path("backups")),
        restartedContainers = strings(node.path("restartedContainers")),
        message = node.path("message").asText("")
    )

    fun verification(node: JsonNode): VerificationOutcome = VerificationOutcome(
        status = node.path("status").asText("UNKNOWN"),
        resolved = node.path("resolved").asBoolean(false),
        summary = node.path("summary").asText(""),
        runtimeChecks = strings(node.path("runtimeChecks")),
        checkedAt = node.path("checkedAt").asText("")
    )

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun JsonNode.toContainer(): ContainerItem? {
        val id = path("id").asText("")
        if (id.isBlank()) return null
        val state = path("state").asText("running")
        val statusText = path("status").asText("Running")
        val healthState = path("health").asText("none")
        val isUnhealthy = healthState.equals("unhealthy", ignoreCase = true) ||
            statusText.contains("unhealthy", ignoreCase = true)
        val isRunning = state.equals("running", ignoreCase = true) ||
            state.equals("restarting", ignoreCase = true) ||
            statusText.startsWith("Up", ignoreCase = true)
        val ports = if (path("ports").isArray) {
            path("ports").map { it.asText() }.filter { it.isNotBlank() }.joinToString(", ")
        } else {
            ""
        }
        val healthLabel = when {
            isUnhealthy -> "Unhealthy"
            healthState.equals("healthy", ignoreCase = true) -> "Healthy"
            healthState.equals("starting", ignoreCase = true) -> "Starting"
            isRunning -> "Healthy"
            else -> "-"
        }
        return ContainerItem(
            id = id,
            name = path("name").asText("").removePrefix("/"),
            image = path("image").asText(""),
            state = state,
            status = statusText,
            health = healthLabel,
            ports = ports.ifBlank { "-" },
            exitCode = path("exitCode").takeIf { it.isInt }?.asInt(),
            restartCount = path("restartCount").asInt(0),
            healthState = healthState,
            isRunning = isRunning,
            isHealthy = healthLabel == "Healthy",
            isUnhealthy = isUnhealthy,
            isStopped = state.equals("exited", ignoreCase = true) ||
                state.equals("dead", ignoreCase = true) ||
                state.equals("created", ignoreCase = true)
        )
    }

    private fun dependencies(node: JsonNode): List<DependencyItem> {
        if (!node.isArray) return emptyList()
        return node.map { dependency ->
            DependencyItem(
                service = dependency.path("service").asText(""),
                container = dependency.path("container").asText(""),
                containerState = dependency.path("containerState").asText(""),
                health = dependency.path("health").asText("none"),
                hostPort = dependency.path("hostPort").asInt(0),
                reachable = dependency.path("reachable").asBoolean(false),
                latencyMs = dependency.path("latencyMs").asLong(-1),
                detail = dependency.path("detail").asText("")
            )
        }
    }

    private fun JsonNode.toFinding(): DiagnosticFinding? {
        val id = path("id").asText("")
        if (id.isBlank()) return null

        val evidence = if (path("evidence").isArray) {
            path("evidence").map { ev ->
                EvidenceItem(
                    source = ev.path("source").asText("UNKNOWN"),
                    location = ev.path("location").asText("unknown"),
                    excerpt = ev.path("excerpt").asText("")
                )
            }
        } else {
            emptyList()
        }

        val repair = path("repair")
        val hasRepair = !repair.isNull && !repair.isMissingNode && !repair.isArray
        val patches = if (hasRepair && repair.path("patches").isArray) {
            repair.path("patches").map { patch ->
                FilePatchItem(
                    path = patch.path("path").asText(),
                    originalSnippet = patch.path("originalSnippet").asText(),
                    replacementSnippet = patch.path("replacementSnippet").asText(),
                    mode = patch.path("mode").asText("REPLACE")
                )
            }
        } else {
            emptyList()
        }
        val actions = if (hasRepair && repair.path("actions").isArray) {
            repair.path("actions").map { action ->
                RepairActionItem(
                    kind = action.path("kind").asText("RESTART_CONTAINER"),
                    target = action.path("target").asText(),
                    description = action.path("description").asText()
                )
            }
        } else {
            emptyList()
        }

        return DiagnosticFinding(
            id = id,
            ruleId = path("ruleId").asText("unknown-rule"),
            title = path("title").asText("Unknown issue").ifBlank { "Unknown issue" },
            severity = path("severity").asText("WARN").uppercase(),
            summary = path("summary").asText("No details available.").ifBlank { "No details available." },
            evidence = evidence,
            repairDescription = if (hasRepair) repair.path("description").asText("").ifBlank { null } else null,
            patches = patches,
            actions = actions,
            risk = if (hasRepair) repair.path("risk").asText("LOW") else "LOW",
            requiresRestart = hasRepair && repair.path("requiresRestart").asBoolean(false),
            confidence = path("confidence").asInt(70),
            category = path("category").asText("configuration"),
            nextSteps = strings(path("nextSteps")),
            fixable = hasRepair && (patches.isNotEmpty() || actions.isNotEmpty())
        )
    }

    private fun strings(node: JsonNode): List<String> {
        if (!node.isArray) return emptyList()
        return node.map { it.asText() }.filter { it.isNotBlank() }
    }
}
