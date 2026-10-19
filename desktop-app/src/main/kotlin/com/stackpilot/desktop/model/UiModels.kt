package com.stackpilot.desktop.model

enum class NavItem(val title: String) {
    Dashboard("Dashboard"),
    Project("Project"),
    Containers("Containers"),
    LiveLogs("Live Logs"),
    Diagnosis("Diagnosis"),
    Repairs("Repairs"),
    Reports("Reports"),
    Settings("Settings")
}

data class ContainerItem(
    val id: String,
    val name: String,
    val image: String,
    val state: String = "running",
    val status: String = "Running",
    val health: String = "Healthy",
    val ports: String = "",
    val isRunning: Boolean = state.equals("running", ignoreCase = true) || status.contains("Up", ignoreCase = true),
    val isHealthy: Boolean = health.equals("Healthy", ignoreCase = true) || (isRunning && !status.contains("unhealthy", ignoreCase = true)),
    val isUnhealthy: Boolean = health.equals("Unhealthy", ignoreCase = true) || status.contains("unhealthy", ignoreCase = true)
)

enum class LogLevel { INFO, ERROR, WARN, DEBUG }

data class LogEntry(
    val timestamp: String = "",
    val level: LogLevel? = null,
    val logger: String = "",
    val message: String,
    val raw: String = ""
)

data class RecentDiagnosticItem(
    val time: String,
    val issue: String,
    val service: String,
    val status: String, // "Detected", "Resolved", "Investigating"
    val statusColor: String // "red", "green", "yellow"
)

enum class DiagnosisTab {
    Explanation,
    Evidence,
    FixProposal,
    Verification
}

data class EvidenceItem(
    val source: String,
    val location: String,
    val excerpt: String
)

data class FilePatchItem(
    val path: String,
    val originalSnippet: String,
    val replacementSnippet: String
)

data class DiagnosticFinding(
    val id: String,
    val ruleId: String,
    val title: String,
    val severity: String,
    val summary: String,
    val evidence: List<EvidenceItem>,
    val repairDescription: String?,
    val patches: List<FilePatchItem>
)

data class ProjectSnapshot(
    val id: String,
    val rootPath: String,
    val name: String,
    val filesCount: Int,
    val files: List<String>
)
