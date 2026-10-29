package com.stackpilot.desktop.model

enum class NavItem(val title: String) {
    Dashboard("Dashboard"),
    Health("Infrastructure"),
    Project("Project"),
    Containers("Containers"),
    LiveLogs("Live Logs"),
    Diagnosis("Diagnosis"),
    Repairs("Repairs"),
    History("History"),
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
    val exitCode: Int? = null,
    val restartCount: Int = 0,
    val healthState: String = "none",
    val isRunning: Boolean = state.equals("running", ignoreCase = true) || status.contains("Up", ignoreCase = true),
    val isHealthy: Boolean = health.equals("Healthy", ignoreCase = true) || (isRunning && !status.contains("unhealthy", ignoreCase = true)),
    val isUnhealthy: Boolean = health.equals("Unhealthy", ignoreCase = true) || status.contains("unhealthy", ignoreCase = true),
    val isStopped: Boolean = state.equals("exited", ignoreCase = true) ||
        state.equals("dead", ignoreCase = true) ||
        state.equals("created", ignoreCase = true)
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
    val replacementSnippet: String,
    val mode: String = "REPLACE"
)

data class RepairActionItem(
    val kind: String,
    val target: String,
    val description: String
)

data class DiagnosticFinding(
    val id: String,
    val ruleId: String,
    val title: String,
    val severity: String,
    val summary: String,
    val evidence: List<EvidenceItem>,
    val repairDescription: String?,
    val patches: List<FilePatchItem>,
    val actions: List<RepairActionItem> = emptyList(),
    val risk: String = "LOW",
    val requiresRestart: Boolean = false,
    val confidence: Int = 70,
    val category: String = "configuration",
    val nextSteps: List<String> = emptyList(),
    val fixable: Boolean = patches.isNotEmpty() || actions.isNotEmpty()
)

data class ProjectSnapshot(
    val id: String,
    val rootPath: String,
    val name: String,
    val filesCount: Int,
    val files: List<String>
)

// ----------------------------------------------------------------------
// Feature 1 – infrastructure health (docker inspect + TCP probes)
// ----------------------------------------------------------------------

data class DependencyItem(
    val service: String,
    val container: String,
    val containerState: String,
    val health: String,
    val hostPort: Int,
    val reachable: Boolean,
    val latencyMs: Long,
    val detail: String
)

data class InfraHealth(
    val overall: String = "UNKNOWN",
    val dockerConnected: Boolean = false,
    val engineName: String = "Docker Engine",
    val dockerError: String? = null,
    val total: Int = 0,
    val running: Int = 0,
    val unhealthy: Int = 0,
    val stopped: Int = 0,
    val containers: List<ContainerItem> = emptyList(),
    val dependencies: List<DependencyItem> = emptyList(),
    val issues: List<String> = emptyList(),
    val checkedAt: String = ""
) {
    val isUp: Boolean get() = overall.equals("UP", ignoreCase = true)
    val isDegraded: Boolean get() = overall.equals("DEGRADED", ignoreCase = true)
}

// ----------------------------------------------------------------------
// Feature 2 – log analysis (grouped failure signatures)
// ----------------------------------------------------------------------

data class SignatureItem(
    val signature: String,
    val category: String,
    val severity: String,
    val count: Int,
    val sample: String,
    val line: Int
)

data class LogAnalysis(
    val containerId: String = "",
    val containerName: String = "",
    val totalLines: Int = 0,
    val errorLines: Int = 0,
    val warnLines: Int = 0,
    val infoLines: Int = 0,
    val categories: List<String> = emptyList(),
    val signatures: List<SignatureItem> = emptyList(),
    val exceptionTypes: List<String> = emptyList()
) {
    val hasProblems: Boolean get() = errorLines > 0 || signatures.any { it.severity == "ERROR" }
}

// ----------------------------------------------------------------------
// Feature 5 / 6 – repair outcome and verification verdict
// ----------------------------------------------------------------------

data class RepairOutcome(
    val applied: Boolean = false,
    val changedFiles: List<String> = emptyList(),
    val backups: List<String> = emptyList(),
    val restartedContainers: List<String> = emptyList(),
    val message: String = ""
)

data class VerificationOutcome(
    val status: String = "UNKNOWN",
    val resolved: Boolean = false,
    val summary: String = "",
    val runtimeChecks: List<String> = emptyList(),
    val checkedAt: String = ""
) {
    val pending: Boolean get() = status.equals("CONFIG_FIXED_RUNTIME_PENDING", ignoreCase = true)
}

// ----------------------------------------------------------------------
// History (persisted by the backend)
// ----------------------------------------------------------------------

data class RunHistoryItem(
    val id: String,
    val createdAt: String,
    val projectId: String?,
    val findings: Int,
    val errors: Int,
    val repairable: Int
)

data class VerificationRecordItem(
    val id: String,
    val at: String,
    val runId: String,
    val ruleId: String,
    val status: String,
    val resolved: Boolean,
    val summary: String
)
