package com.stackpilot.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fasterxml.jackson.databind.JsonNode
import com.stackpilot.desktop.BackendClient
import com.stackpilot.desktop.components.*
import com.stackpilot.desktop.model.*
import com.stackpilot.desktop.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser

@Composable
fun DashboardScreen(
    backendPort: Int,
    backendUrl: String,
    sessionToken: String
) {
    val client = remember(backendUrl, sessionToken) { BackendClient(backendUrl, sessionToken) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedNav by remember { mutableStateOf(NavItem.Dashboard) }
    var busy by remember { mutableStateOf(false) }

    // Project state
    var projectPath by remember {
        mutableStateOf(File(".").canonicalFile.absolutePath)
    }
    var projectName by remember { mutableStateOf(File(projectPath).name) }
    var projectId by remember { mutableStateOf<String?>(null) }
    var projectFiles by remember { mutableStateOf<List<JsonNode>>(emptyList()) }

    // Docker state
    var dockerConnected by remember { mutableStateOf(false) }
    var dockerEngineName by remember { mutableStateOf("Docker Engine") }
    var dockerError by remember { mutableStateOf<String?>(null) }
    var containers by remember { mutableStateOf<List<ContainerItem>>(emptyList()) }
    var selectedContainerId by remember { mutableStateOf<String?>(null) }
    var selectedContainerName by remember { mutableStateOf("shop-api") }

    // Logs state
    var logEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    // Diagnostics state
    var diagnosticRunId by remember { mutableStateOf<String?>(null) }
    var findings by remember { mutableStateOf<List<DiagnosticFinding>>(emptyList()) }
    var selectedFinding by remember { mutableStateOf<DiagnosticFinding?>(null) }
    var verifySummaryMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var recentDiagnostics by remember { mutableStateOf<List<RecentDiagnosticItem>>(emptyList()) }

    // Helper for IO coroutines
    fun runIo(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    // Select container and stream its logs immediately
    fun selectContainer(container: ContainerItem) {
        selectedContainerId = container.id
        selectedContainerName = container.name
        logEntries = emptyList() // Clear previous container's logs immediately
        scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { client.logs(container.id, 500) }
                if (raw.isNotBlank()) {
                    logEntries = raw.lines().filter { it.isNotBlank() }.map { parseLogLine(it) }
                }
            } catch (e: Exception) {
                logEntries = listOf(LogEntry(message = "Error fetching logs: ${e.message}", raw = ""))
            }
        }
    }

    // Refresh Docker status and containers
    fun refreshDocker() = runIo {
        val status = client.dockerStatus()
        dockerConnected = status.path("connected").asBoolean(false)
        dockerEngineName = status.path("engineName").asText("Docker Engine")
        dockerError = status.path("error").takeIf { !it.isMissingNode && !it.isNull }?.asText()

        val listNode = client.containers()
        if (listNode.isArray && listNode.size() > 0) {
            val loaded = listNode.map { node ->
                val id = node.path("id").asText()
                val name = node.path("name").asText().removePrefix("/")
                val image = node.path("image").asText()
                val state = node.path("state").asText("running")
                val statusText = node.path("status").asText("Running")
                val portsList = node.path("ports").map { it.asText() }.filter { it.isNotBlank() }.joinToString(", ")

                val isUnhealthy = statusText.contains("unhealthy", ignoreCase = true)
                val isHealthy = statusText.contains("healthy", ignoreCase = true) || (!isUnhealthy && state.contains("running", ignoreCase = true))

                ContainerItem(
                    id = id,
                    name = name,
                    image = image,
                    state = state,
                    status = statusText,
                    health = if (isUnhealthy) "Unhealthy" else if (isHealthy) "Healthy" else "-",
                    ports = portsList.ifBlank { "-" }
                )
            }
            containers = loaded
            val current = loaded.find { it.id == selectedContainerId || it.name == selectedContainerName }
            if (current != null) {
                selectContainer(current)
            } else if (loaded.isNotEmpty()) {
                selectContainer(loaded.first())
            }
        } else {
            containers = emptyList()
            selectedContainerId = null
            selectedContainerName = "No container running"
            logEntries = emptyList()
        }
    }

    // Attach project and index configuration
    fun attachCurrentProject(path: String) = runIo {
        val snapshot = client.attachProject(path)
        projectId = snapshot.path("id").asText()
        projectName = File(path).name
        projectPath = path
        projectFiles = snapshot.path("files").toList()

        // Automatically run diagnostics on the newly attached project
        val run = client.runDiagnostics(projectId)
        diagnosticRunId = run.path("id").asText()
        val parsedFindings = parseFindings(run.path("findings"))
        findings = parsedFindings
        selectedFinding = parsedFindings.firstOrNull()
        verifySummaryMap = emptyMap()

        // Add to recent diagnostics
        val nowTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val newRecent = parsedFindings.map { f ->
            RecentDiagnosticItem(
                time        = nowTime,
                issue       = f.title,
                service     = findingServiceName(f.ruleId),
                status      = "Detected",
                statusColor = findingStatusColor(f.severity)
            )
        }
        recentDiagnostics = newRecent
    }

    // Run diagnostics explicitly
    fun runDiagnostics() = runIo {
        val run = client.runDiagnostics(projectId)
        diagnosticRunId = run.path("id").asText()
        val parsedFindings = parseFindings(run.path("findings"))
        findings = parsedFindings
        selectedFinding = parsedFindings.firstOrNull()
        verifySummaryMap = emptyMap()

        val nowTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val newRecent = parsedFindings.map { f ->
            RecentDiagnosticItem(
                time        = nowTime,
                issue       = f.title,
                service     = findingServiceName(f.ruleId),
                status      = "Detected",
                statusColor = findingStatusColor(f.severity)
            )
        }
        if (newRecent.isNotEmpty()) {
            recentDiagnostics = newRecent + recentDiagnostics.take(5)
        }
        snackbarHostState.showSnackbar("Diagnostics complete: ${parsedFindings.size} issue(s) detected")
    }

    // Approve and apply real repair
    fun applyRepair(finding: DiagnosticFinding) = runIo {
        val runId = diagnosticRunId ?: return@runIo
        val result = client.approveRepair(runId, finding.id)
        val msg = result.path("message").asText("Repair applied successfully")
        snackbarHostState.showSnackbar(msg)

        // Automatically verify after applying
        val verifyResult = client.verify(runId, finding.id)
        val resolved = verifyResult.path("resolved").asBoolean(false)
        val summary = verifyResult.path("summary").asText()
        verifySummaryMap = verifySummaryMap + (finding.id to summary)

        recentDiagnostics = recentDiagnostics.map { item ->
            if (item.issue == finding.title) {
                item.copy(
                    status = if (resolved) "Resolved" else "Investigating",
                    statusColor = if (resolved) "green" else "yellow"
                )
            } else item
        }
    }

    // Run verification on finding
    fun verifyFinding(finding: DiagnosticFinding) = runIo {
        val runId = diagnosticRunId ?: return@runIo
        val verifyResult = client.verify(runId, finding.id)
        val resolved = verifyResult.path("resolved").asBoolean(false)
        val summary = verifyResult.path("summary").asText()
        verifySummaryMap = verifySummaryMap + (finding.id to summary)
        snackbarHostState.showSnackbar(if (resolved) "✓ Issue verified as resolved!" else "⚠ Issue still present.")
    }

    // Initial setup on mount: load Docker and attach project
    LaunchedEffect(Unit) {
        refreshDocker()
        attachCurrentProject(projectPath)
    }

    // Periodic live log tailing for the chosen container
    LaunchedEffect(selectedContainerId) {
        val containerId = selectedContainerId ?: return@LaunchedEffect
        while (true) {
            try {
                val raw = withContext(Dispatchers.IO) { client.logs(containerId, 500) }
                if (raw.isNotBlank()) {
                    logEntries = raw.lines().filter { it.isNotBlank() }.map { parseLogLine(it) }
                }
            } catch (_: Exception) {
                // If container has no logs or error, preserve current display
            }
            delay(2000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        // Left Sidebar Navigation
        Sidebar(
            selectedItem = selectedNav,
            onItemSelected = { selectedNav = it },
            dockerConnected = dockerConnected,
            backendPort = backendPort
        )

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                TopBar(
                    projectName = projectName,
                    projectPath = projectPath,
                    onChangeProject = {
                        val chooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = "Select Project / Lab Folder to Index"
                            selectedFile = File(projectPath)
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val selected = chooser.selectedFile
                            attachCurrentProject(selected.absolutePath)
                        }
                    }
                )

                // Tab Routing based on Sidebar selection
                when (selectedNav) {
                    NavItem.Dashboard -> {
                        DashboardMainView(
                            containers = containers,
                            selectedContainerId = selectedContainerId,
                            selectedContainerName = selectedContainerName,
                            logEntries = logEntries,
                            recentDiagnostics = recentDiagnostics,
                            findings = findings,
                            selectedFinding = selectedFinding,
                            verifySummary = selectedFinding?.id?.let { verifySummaryMap[it] },
                            isBusy = busy,
                            onRefreshDocker = { refreshDocker() },
                            onSelectContainer = { container ->
                                selectContainer(container)
                            },
                            onRunDiagnostics = { runDiagnostics() },
                            onApplyRepair = { applyRepair(it) },
                            onVerifyFinding = { verifyFinding(it) },
                            onStartLab = {
                                val labDir = File("labs/redis-hostname-mismatch")
                                if (labDir.exists()) {
                                    attachCurrentProject(labDir.absolutePath)
                                    scope.launch { snackbarHostState.showSnackbar("Attached lab: redis-hostname-mismatch") }
                                }
                            },
                            onOpenDocs = {
                                selectedNav = NavItem.Project
                            }
                        )
                    }

                    NavItem.Project -> {
                        ProjectFullView(
                            projectPath = projectPath,
                            projectId = projectId,
                            files = projectFiles,
                            isBusy = busy,
                            onBrowse = {
                                val chooser = JFileChooser().apply {
                                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                    dialogTitle = "Select Project Folder"
                                }
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    attachCurrentProject(chooser.selectedFile.absolutePath)
                                }
                            },
                            onReindex = {
                                attachCurrentProject(projectPath)
                            }
                        )
                    }

                    NavItem.Containers -> {
                        ContainersFullView(
                            containers = containers,
                            dockerConnected = dockerConnected,
                            dockerEngineName = dockerEngineName,
                            dockerError = dockerError,
                            onRefresh = { refreshDocker() },
                            onSelectLogs = { container ->
                                selectContainer(container)
                                selectedNav = NavItem.LiveLogs
                            }
                        )
                    }

                    NavItem.LiveLogs -> {
                        LiveLogsFullView(
                            containerName = selectedContainerName,
                            containerId = selectedContainerId,
                            containers = containers,
                            logs = logEntries,
                            onSelectContainer = { container ->
                                selectContainer(container)
                            }
                        )
                    }

                    NavItem.Diagnosis -> {
                        DiagnosisFullView(
                            findings = findings,
                            selectedFinding = selectedFinding,
                            verifySummary = selectedFinding?.id?.let { verifySummaryMap[it] },
                            isBusy = busy,
                            onSelectFinding = { selectedFinding = it },
                            onRun = { runDiagnostics() },
                            onApply = { applyRepair(it) },
                            onVerify = { verifyFinding(it) }
                        )
                    }

                    NavItem.Repairs -> {
                        RepairsFullView(
                            findings = findings,
                            onApply = { applyRepair(it) }
                        )
                    }

                    NavItem.Reports -> {
                        ReportsFullView(
                            projectName = projectName,
                            projectPath = projectPath,
                            containersCount = containers.size,
                            findings = findings,
                            recentDiagnostics = recentDiagnostics
                        )
                    }

                    NavItem.Settings -> {
                        SettingsFullView(
                            backendUrl = backendUrl,
                            backendPort = backendPort,
                            sessionToken = sessionToken,
                            dockerConnected = dockerConnected,
                            dockerEngineName = dockerEngineName
                        )
                    }
                }
            }

            // Snackbar Notification Display
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun DashboardMainView(
    containers: List<ContainerItem>,
    selectedContainerId: String?,
    selectedContainerName: String,
    logEntries: List<LogEntry>,
    recentDiagnostics: List<RecentDiagnosticItem>,
    findings: List<DiagnosticFinding>,
    selectedFinding: DiagnosticFinding?,
    verifySummary: String?,
    isBusy: Boolean,
    onRefreshDocker: () -> Unit,
    onSelectContainer: (ContainerItem) -> Unit,
    onRunDiagnostics: () -> Unit,
    onApplyRepair: (DiagnosticFinding) -> Unit,
    onVerifyFinding: (DiagnosticFinding) -> Unit,
    onStartLab: () -> Unit,
    onOpenDocs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Metric Stat Cards Row
        StatCardsRow(
            totalContainers = containers.size,
            runningCount = containers.count { it.isRunning },
            unhealthyCount = containers.count { it.isUnhealthy },
            stoppedCount = containers.count { !it.isRunning }
        )

        // 2-Column Split Layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier.weight(0.64f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Containers Table
                ContainersTable(
                    containers = containers,
                    selectedContainerId = selectedContainerId,
                    onViewLogs = { container ->
                        onSelectContainer(container)
                    },
                    onRefresh = onRefreshDocker
                )

                // Live Logs Terminal for this particular container
                LiveLogsView(
                    containerName = selectedContainerName,
                    containerId = selectedContainerId,
                    containersList = containers,
                    logEntries = logEntries,
                    onSelectContainer = onSelectContainer
                )

                // Recent Diagnostics
                RecentDiagnosticsView(
                    items = recentDiagnostics,
                    onViewAll = onRunDiagnostics,
                    onSelectItem = { /* item clicked */ }
                )
            }

            // Right Column
            Column(
                modifier = Modifier.weight(0.36f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Quick Actions
                QuickActions(
                    onRunDiagnostics = onRunDiagnostics,
                    onStartLab = onStartLab,
                    onOpenDocs = onOpenDocs
                )

                // Diagnosis Result with real findings
                DiagnosisResultView(
                    finding = selectedFinding,
                    verifySummary = verifySummary,
                    isBusy = isBusy,
                    onRunAgain = onRunDiagnostics,
                    onReviewAndApplyFix = onApplyRepair,
                    onVerify = onVerifyFinding
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------- Sub-Views for All Sidebar Nav Items ----------------------

@Composable
private fun ProjectFullView(
    projectPath: String,
    projectId: String?,
    files: List<JsonNode>,
    isBusy: Boolean,
    onBrowse: () -> Unit,
    onReindex: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Project Configuration & Documentation", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Text(
            "StackPilot parses Compose files, application properties, and markdown documentation so diagnosis rules cite exact evidence lines.",
            fontSize = 12.sp,
            color = AppColors.TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(8.dp)),
                color = AppColors.CardBackground
            ) {
                Text(
                    text = projectPath,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextPrimary
                )
            }

            Button(
                onClick = onBrowse,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.CardBackgroundElevated)
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Browse", fontSize = 12.sp)
            }

            Button(
                onClick = onReindex,
                enabled = !isBusy,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryBlue)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Re-index", fontSize = 12.sp)
            }
        }

        if (projectId != null) {
            Text("Snapshot ID: $projectId · ${files.size} indexed configuration file(s)", fontSize = 11.sp, color = AppColors.TextMuted)
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
            color = AppColors.CardBackground
        ) {
            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No indexed files found in current directory.", color = AppColors.TextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(files) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Description, contentDescription = null, tint = AppColors.PrimaryBlueHover, modifier = Modifier.size(16.dp))
                                Text(file.path("relativePath").asText(), fontSize = 12.sp, color = AppColors.TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Text(file.path("kind").asText(), fontSize = 11.sp, color = AppColors.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainersFullView(
    containers: List<ContainerItem>,
    dockerConnected: Boolean,
    dockerEngineName: String,
    dockerError: String?,
    onRefresh: () -> Unit,
    onSelectLogs: (ContainerItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Docker Containers ($dockerEngineName)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text(
                    if (dockerConnected) "Connected to Docker daemon · ${containers.size} active container(s)"
                    else dockerError ?: "Docker daemon not reachable.",
                    fontSize = 12.sp,
                    color = if (dockerConnected) AppColors.StatusGreen else AppColors.StatusRed
                )
            }
            Button(onClick = onRefresh, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh Containers", fontSize = 12.sp)
            }
        }

        ContainersTable(
            containers = containers,
            selectedContainerId = null,
            onViewLogs = onSelectLogs,
            onRefresh = onRefresh
        )
    }
}

@Composable
private fun LiveLogsFullView(
    containerName: String,
    containerId: String?,
    containers: List<ContainerItem>,
    logs: List<LogEntry>,
    onSelectContainer: (ContainerItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Container Log Console", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        LiveLogsView(
            containerName = containerName,
            containerId = containerId,
            containersList = containers,
            logEntries = logs,
            onSelectContainer = onSelectContainer,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun DiagnosisFullView(
    findings: List<DiagnosticFinding>,
    selectedFinding: DiagnosticFinding?,
    verifySummary: String?,
    isBusy: Boolean,
    onSelectFinding: (DiagnosticFinding) -> Unit,
    onRun: () -> Unit,
    onApply: (DiagnosticFinding) -> Unit,
    onVerify: (DiagnosticFinding) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Evidence-Based Diagnostics", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text("Evaluated against container logs, Docker Compose topologies, and Spring Boot bindings.", fontSize = 12.sp, color = AppColors.TextMuted)
            }
            Button(onClick = onRun, enabled = !isBusy, shape = RoundedCornerShape(8.dp)) {
                Text("Run Diagnostics", fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Findings list on left
            Surface(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
                color = AppColors.CardBackground
            ) {
                if (findings.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active findings.", color = AppColors.TextMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(findings) { f ->
                            val isSelected = f.id == selectedFinding?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AppColors.PrimaryBlue.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) AppColors.PrimaryBlue else AppColors.CardBorder, RoundedCornerShape(8.dp))
                                    .clickable { onSelectFinding(f) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AppColors.StatusRed)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(f.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                                    Text(f.ruleId, fontSize = 10.sp, color = AppColors.TextMuted)
                                }
                            }
                        }
                    }
                }
            }

            // Finding details on right
            DiagnosisResultView(
                finding = selectedFinding,
                verifySummary = verifySummary,
                isBusy = isBusy,
                onRunAgain = onRun,
                onReviewAndApplyFix = onApply,
                onVerify = onVerify,
                modifier = Modifier.weight(0.6f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun RepairsFullView(
    findings: List<DiagnosticFinding>,
    onApply: (DiagnosticFinding) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Automated Repairs & Code Patches", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Text("Review unified diffs before writing changes to disk.", fontSize = 12.sp, color = AppColors.TextSecondary)

        val repairable = findings.filter { it.patches.isNotEmpty() }
        if (repairable.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
                color = AppColors.CardBackground
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pending patches. Run diagnostics to detect configuration mismatches.", color = AppColors.TextMuted)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(repairable) { finding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
                        color = AppColors.CardBackground
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(finding.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                            Text(finding.repairDescription ?: "", fontSize = 12.sp, color = AppColors.TextSecondary)
                            finding.patches.forEach { patch ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF090D16)),
                                    color = Color(0xFF090D16)
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(patch.path, fontSize = 11.sp, color = AppColors.StatusGreen, fontFamily = FontFamily.Monospace)
                                        Text("- ${patch.originalSnippet}", fontSize = 11.sp, color = AppColors.StatusRed, fontFamily = FontFamily.Monospace)
                                        Text("+ ${patch.replacementSnippet}", fontSize = 11.sp, color = AppColors.StatusGreen, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                            Button(
                                onClick = { onApply(finding) },
                                modifier = Modifier.align(Alignment.End),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Apply Patch", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsFullView(
    projectName: String,
    projectPath: String,
    containersCount: Int,
    findings: List<DiagnosticFinding>,
    recentDiagnostics: List<RecentDiagnosticItem>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Diagnostics & Environment Report", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
            color = AppColors.CardBackground
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Project Overview", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.PrimaryBlueHover)
                Text("Target: $projectName ($projectPath)", fontSize = 12.sp, color = AppColors.TextPrimary)
                Text("Monitored Containers: $containersCount", fontSize = 12.sp, color = AppColors.TextPrimary)
                Text("Detected Issues: ${findings.size}", fontSize = 12.sp, color = if (findings.isEmpty()) AppColors.StatusGreen else AppColors.StatusRed)
            }
        }

        RecentDiagnosticsView(
            items = recentDiagnostics,
            onViewAll = {},
            onSelectItem = {}
        )
    }
}

@Composable
private fun SettingsFullView(
    backendUrl: String,
    backendPort: Int,
    sessionToken: String,
    dockerConnected: Boolean,
    dockerEngineName: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("System & Connection Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
            color = AppColors.CardBackground
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingField("Backend Endpoint", backendUrl)
                SettingField("Local Port", backendPort.toString())
                SettingField("Session Auth Token", sessionToken.take(8) + "••••••••")
                SettingField("Docker Engine", "$dockerEngineName (${if (dockerConnected) "Online" else "Offline"})")
            }
        }
    }
}

@Composable
private fun SettingField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = AppColors.TextMuted)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AppColors.TextPrimary)
    }
}

// ---------------------- Parsers & Helper Functions ----------------------

/**
 * Derives a short human-readable service name from a ruleId for display in
 * the Recent Diagnostics table.
 *
 * Examples:
 *   "redis-hostname-mismatch"  → "redis"
 *   "kafka-bootstrap-mismatch" → "kafka"
 *   "container-unhealthy"      → "docker"
 *   "spring-boot-startup-failure" → "spring"
 */
private fun findingServiceName(ruleId: String): String = when {
    ruleId.startsWith("redis")     -> "redis"
    ruleId.startsWith("kafka")     -> "kafka"
    ruleId.startsWith("container") -> "docker"
    ruleId.startsWith("spring")    -> "spring"
    else                           -> ruleId.substringBefore("-").ifBlank { "app" }
}

/** Maps a severity string to a status-dot colour key used by RecentDiagnosticsView. */
private fun findingStatusColor(severity: String): String = when (severity.uppercase()) {
    "ERROR", "HIGH" -> "red"
    "WARN"          -> "yellow"
    else            -> "yellow"
}

private fun parseFindings(nodes: JsonNode): List<DiagnosticFinding> {
    if (!nodes.isArray) return emptyList()
    return nodes.mapNotNull { f ->
        // Guard: skip any finding that has no id — an empty id would break the repair API call.
        val id = f.path("id").asText("").ifBlank { return@mapNotNull null }

        val evidenceList = if (f.path("evidence").isArray) {
            f.path("evidence").map { ev ->
                EvidenceItem(
                    source   = ev.path("source").asText("UNKNOWN"),
                    location = ev.path("location").asText("unknown"),
                    excerpt  = ev.path("excerpt").asText("")
                )
            }
        } else emptyList()

        val repair = f.path("repair")
        val patchesList = if (!repair.isNull && !repair.isMissingNode && repair.path("patches").isArray) {
            repair.path("patches").map { p ->
                FilePatchItem(
                    path               = p.path("path").asText(),
                    originalSnippet    = p.path("originalSnippet").asText(),
                    replacementSnippet = p.path("replacementSnippet").asText()
                )
            }
        } else emptyList()

        val repairDesc = if (!repair.isNull && !repair.isMissingNode) {
            repair.path("description").takeIf { !it.isMissingNode && !it.isNull }?.asText()
        } else null

        // Use the actual severity from the backend; do NOT default to HIGH — that would
        // mis-classify WARN findings (e.g. ContainerUnhealthyRule) as critical.
        val severity = f.path("severity").asText("WARN").uppercase()

        DiagnosticFinding(
            id                = id,
            ruleId            = f.path("ruleId").asText("unknown-rule"),
            title             = f.path("title").asText("Unknown issue").ifBlank { "Unknown issue" },
            severity          = severity,
            summary           = f.path("summary").asText("No details available.").ifBlank { "No details available." },
            evidence          = evidenceList,
            repairDescription = repairDesc,
            patches           = patchesList
        )
    }
}

private fun parseLogLine(raw: String): LogEntry {
    // Strip residual binary header bytes that may survive sanitisation
    // (e.g. if the Docker transport delivers them inside the payload string).
    val cleaned = raw.trimStart { it.code < 0x20 && it != '\t' && it != '\r' && it != '\n' }
        .trimEnd()

    if (cleaned.isBlank()) {
        return LogEntry(message = "", raw = raw)
    }

    // ── 1. Docker RFC3339 timestamp prefix ────────────────────────────────
    // Docker adds timestamps in the form: "2024-01-15T10:22:31.245123456Z "
    // or with timezone offset: "2026-10-16T10:09:15.123456789+05:30 "
    val dockerTsRegex = Regex(
        """^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2}))\s+(.*)$"""
    )
    val (timestamp, afterTs) = dockerTsRegex.find(cleaned)?.let {
        val raw_ts = it.groupValues[1]
        // Normalise to "YYYY-MM-DD HH:MM:SS" for display
        val displayTs = raw_ts.take(19).replace('T', ' ')
        Pair(displayTs, it.groupValues[2])
    } ?: Pair("", cleaned)

    val remaining = afterTs.trim()

    // ── 2. Spring Boot structured log format ──────────────────────────────
    // Spring Boot 3.x: "2026-10-16T15:39:12.382+05:30  INFO 19632 --- [threadName] logger.Name  : message"
    // After stripping the Docker timestamp we may have just the Spring part.
    val springFullRegex = Regex(
        """^(?:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})\s+)?""" +
        """(INFO|WARN|ERROR|DEBUG|TRACE)\s+\d+\s+---\s+\[([^\]]*)\]\s+([\w.${'$'}]+)\s*:\s*(.+)$"""
    )
    springFullRegex.find(remaining)?.let { m ->
        val level = m.groupValues[1].toLogLevel()
        val logger = m.groupValues[3].abbreviateLogger()
        val message = m.groupValues[4]
        return LogEntry(
            timestamp = timestamp.ifBlank {
                // Docker timestamp may be embedded in the Spring header
                m.groupValues[0].take(19).replace('T', ' ').trim()
            },
            level = level,
            logger = logger,
            message = message,
            raw = raw
        )
    }

    // ── 3. Simple "LEVEL  logger : message" (no PID/thread) ───────────────
    val springShortRegex = Regex(
        """^(INFO|WARN|ERROR|DEBUG|TRACE)\s+([\w.${'$'}]+(?:\.[\w${'$'}]+)*)\s*:\s*(.+)$"""
    )
    springShortRegex.find(remaining)?.let { m ->
        return LogEntry(
            timestamp = timestamp,
            level = m.groupValues[1].toLogLevel(),
            logger = m.groupValues[2].abbreviateLogger(),
            message = m.groupValues[3],
            raw = raw
        )
    }

    // ── 4. Level keyword anywhere in the line ────────────────────────────
    val upperRemaining = remaining.uppercase()
    val detectedLevel: LogLevel? = when {
        " ERROR " in upperRemaining || upperRemaining.startsWith("ERROR")
            || "EXCEPTION" in upperRemaining || "FAILED" in upperRemaining
            || "FATAL" in upperRemaining -> LogLevel.ERROR
        " WARN " in upperRemaining || upperRemaining.startsWith("WARN") -> LogLevel.WARN
        " DEBUG " in upperRemaining || upperRemaining.startsWith("DEBUG")
            || " TRACE " in upperRemaining -> LogLevel.DEBUG
        " INFO " in upperRemaining || upperRemaining.startsWith("INFO") -> LogLevel.INFO
        else -> null
    }

    return LogEntry(
        timestamp = timestamp,
        level = detectedLevel,
        logger = "",
        message = remaining.ifBlank { raw },
        raw = raw
    )
}

/** Converts a log-level string to [LogLevel]. */
private fun String.toLogLevel(): LogLevel? = when (this.uppercase()) {
    "INFO"  -> LogLevel.INFO
    "WARN"  -> LogLevel.WARN
    "ERROR" -> LogLevel.ERROR
    "DEBUG", "TRACE" -> LogLevel.DEBUG
    else -> null
}

/**
 * Abbreviates a fully qualified logger name for display.
 * e.g. "com.stackpilot.backend.api.DockerController" → "c.s.b.a.DockerController"
 */
private fun String.abbreviateLogger(): String {
    val parts = split('.')
    if (parts.size <= 2) return this
    val abbreviated = parts.dropLast(1).joinToString(".") { it.take(1) }
    return "$abbreviated.${parts.last()}"
}
