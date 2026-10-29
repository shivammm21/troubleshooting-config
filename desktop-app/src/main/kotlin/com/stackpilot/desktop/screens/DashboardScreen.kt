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
import com.stackpilot.desktop.ApiParsers
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
    sessionToken: String,
    userName: String,
    userEmail: String,
    onLogout: () -> Unit
) {
    val client = remember(backendUrl, sessionToken) { BackendClient(backendUrl, sessionToken) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedNav by remember { mutableStateOf(NavItem.Dashboard) }
    var busy by remember { mutableStateOf(false) }

    // Profile panel opened from the top-bar account menu
    var showProfile by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

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
    var verificationsByFinding by remember { mutableStateOf<Map<String, VerificationOutcome>>(emptyMap()) }
    var rolledBackFindings by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recentDiagnostics by remember { mutableStateOf<List<RecentDiagnosticItem>>(emptyList()) }

    // Feature 5 – finding awaiting explicit developer approval (dialog gate)
    var pendingRepair by remember { mutableStateOf<DiagnosticFinding?>(null) }

    // Feature 1 – infrastructure health: docker state + container health + TCP probes
    var health by remember { mutableStateOf(InfraHealth()) }

    // Feature 2 – grouped failure signatures for the selected container
    var logAnalysis by remember { mutableStateOf<LogAnalysis?>(null) }
    var logAnalysisBusy by remember { mutableStateOf(false) }

    // Persisted history (diagnostic runs + verification verdicts)
    var runHistory by remember { mutableStateOf<List<RunHistoryItem>>(emptyList()) }
    var verificationHistory by remember { mutableStateOf<List<VerificationRecordItem>>(emptyList()) }

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

    // Feature 2: refresh the grouped failure signatures for one container
    fun loadLogAnalysis(containerId: String?) {
        val id = containerId ?: return
        scope.launch {
            logAnalysisBusy = true
            try {
                val node = withContext(Dispatchers.IO) { client.logAnalysis(id, 400) }
                logAnalysis = ApiParsers.logAnalysis(node)
            } catch (e: Exception) {
                logAnalysis = null
                snackbarHostState.showSnackbar("Log analysis failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                logAnalysisBusy = false
            }
        }
    }

    // Select container and stream its logs immediately
    fun selectContainer(container: ContainerItem) {
        selectedContainerId = container.id
        selectedContainerName = container.name
        logEntries = emptyList() // Clear previous container's logs immediately
        logAnalysis = null
        scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { client.logs(container.id, 500) }
                if (raw.isNotBlank()) {
                    logEntries = parseLogEntries(raw)
                }
            } catch (e: Exception) {
                logEntries = listOf(LogEntry(message = "Error fetching logs: ${e.message}", raw = ""))
            }
        }
        loadLogAnalysis(container.id)
    }

    // Feature 1: one call returns Docker state, container health and TCP probe results
    suspend fun loadHealth() {
        val parsed = ApiParsers.health(client.infraHealth())
        health = parsed
        dockerConnected = parsed.dockerConnected
        dockerEngineName = parsed.engineName
        dockerError = parsed.dockerError
        containers = parsed.containers

        if (parsed.containers.isNotEmpty()) {
            val current = parsed.containers.find { it.id == selectedContainerId || it.name == selectedContainerName }
            selectContainer(current ?: parsed.containers.first())
        } else {
            selectedContainerId = null
            selectedContainerName = "No container running"
            logEntries = emptyList()
            logAnalysis = null
        }
    }

    suspend fun loadHistory() {
        runHistory = ApiParsers.runHistory(client.history(30))
        verificationHistory = ApiParsers.verifications(client.verifications(30))
    }

    // Refresh Docker status, container health and probes
    fun refreshDocker() = runIo { loadHealth() }

    // Keep the dashboard "recent diagnostics" list in sync with a verification verdict
    fun updateRecentStatus(finding: DiagnosticFinding, verdict: VerificationOutcome) {
        val label = when {
            verdict.resolved -> "Resolved"
            verdict.pending -> "Pending restart"
            verdict.status.equals("NO_FIX", ignoreCase = true) -> "Manual fix"
            else -> "Unresolved"
        }
        val color = when {
            verdict.resolved -> "green"
            verdict.pending -> "yellow"
            else -> "red"
        }
        recentDiagnostics = recentDiagnostics.map { item ->
            if (item.issue == finding.title) item.copy(status = label, statusColor = color) else item
        }
    }

    fun verdictHeadline(verdict: VerificationOutcome): String = when {
        verdict.resolved -> "Verified as resolved"
        verdict.pending -> "Config fixed - restart the container to load it"
        else -> "Issue is still present"
    }

    // Shared bookkeeping after any diagnostic run (fresh or reopened from history)
    suspend fun applyRun(runNode: JsonNode, announce: Boolean): Int {
        diagnosticRunId = runNode.path("id").asText()
        val parsedFindings = ApiParsers.findings(runNode.path("findings"))
        findings = parsedFindings
        selectedFinding = parsedFindings.firstOrNull()
        verificationsByFinding = emptyMap()
        rolledBackFindings = emptySet()

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
        recentDiagnostics =
            if (announce) (newRecent + recentDiagnostics).distinctBy { it.issue }.take(8) else newRecent

        loadHistory()
        if (announce) {
            snackbarHostState.showSnackbar("Diagnostics complete: ${parsedFindings.size} issue(s) detected")
        }
        return parsedFindings.size
    }

    // Attach project and index configuration, then diagnose it immediately
    fun attachCurrentProject(path: String) = runIo {
        val snapshot = client.attachProject(path)
        projectId = snapshot.path("id").asText()
        projectName = File(path).name
        projectPath = path
        projectFiles = snapshot.path("files").toList()

        // Automatically run diagnostics on the newly attached project
        applyRun(client.runDiagnostics(projectId), announce = false)
    }

    // Run diagnostics explicitly
    fun runDiagnostics() = runIo {
        applyRun(client.runDiagnostics(projectId), announce = true)
    }

    // Feature 5: the developer confirmed this exact patch in the approval dialog
    fun confirmRepair(finding: DiagnosticFinding, restart: Boolean) = runIo {
        pendingRepair = null
        val runId = diagnosticRunId
        if (runId == null) {
            snackbarHostState.showSnackbar("Run diagnostics before applying a fix.")
            return@runIo
        }

        val outcome = ApiParsers.repairOutcome(client.approveRepair(runId, finding.id, restart))
        snackbarHostState.showSnackbar(
            outcome.message.ifBlank { if (outcome.applied) "Fix applied" else "Repair was not applied" }
        )
        if (outcome.applied) {
            rolledBackFindings = rolledBackFindings - finding.id
        }

        // Feature 6: verify immediately after the fix
        val verdict = ApiParsers.verification(client.verify(runId, finding.id))
        verificationsByFinding = verificationsByFinding + (finding.id to verdict)
        updateRecentStatus(finding, verdict)

        loadHealth()
        loadHistory()
    }

    // Restore the on-disk backups created by an approved fix
    fun rollbackFinding(finding: DiagnosticFinding) = runIo {
        val runId = diagnosticRunId
        if (runId == null) {
            snackbarHostState.showSnackbar("Run diagnostics before rolling a fix back.")
            return@runIo
        }
        val outcome = ApiParsers.repairOutcome(client.rollbackRepair(runId, finding.id))
        rolledBackFindings = rolledBackFindings + finding.id
        verificationsByFinding = verificationsByFinding - finding.id
        snackbarHostState.showSnackbar(outcome.message.ifBlank { "Fix rolled back from backup" })
        loadHealth()
        loadHistory()
    }

    // Feature 6: manual re-verification of the selected finding
    fun verifyFinding(finding: DiagnosticFinding) = runIo {
        val runId = diagnosticRunId
        if (runId == null) {
            snackbarHostState.showSnackbar("Run diagnostics first.")
            return@runIo
        }
        val verdict = ApiParsers.verification(client.verify(runId, finding.id))
        verificationsByFinding = verificationsByFinding + (finding.id to verdict)
        updateRecentStatus(finding, verdict)
        loadHistory()
        snackbarHostState.showSnackbar(verdictHeadline(verdict))
    }

    // Container lifecycle actions straight from the health panel
    fun restartContainer(container: ContainerItem) = runIo {
        client.restartContainer(container.id)
        snackbarHostState.showSnackbar("Restarted ${container.name}")
        loadHealth()
    }

    fun startContainer(container: ContainerItem) = runIo {
        client.startContainer(container.id)
        snackbarHostState.showSnackbar("Started ${container.name}")
        loadHealth()
    }

    // Reopen an earlier run from the persisted history
    fun openRun(runId: String) = runIo {
        val count = applyRun(client.run(runId), announce = false)
        selectedNav = NavItem.Diagnosis
        snackbarHostState.showSnackbar("Loaded run ${runId.take(8)}: $count finding(s)")
    }

    // Save the Markdown report generated by the backend
    fun exportReport() {
        val runId = diagnosticRunId
        if (runId == null) {
            scope.launch { snackbarHostState.showSnackbar("Run diagnostics first - a report belongs to one run.") }
            return
        }
        scope.launch {
            try {
                val markdown = withContext(Dispatchers.IO) { client.report(runId) }
                val chooser = JFileChooser().apply {
                    dialogTitle = "Save StackPilot diagnostic report"
                    selectedFile = File("stackpilot-report-${runId.take(8)}.md")
                }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    withContext(Dispatchers.IO) { chooser.selectedFile.writeText(markdown) }
                    snackbarHostState.showSnackbar("Report saved to ${chooser.selectedFile.name}")
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Report export failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // Initial setup on mount: load Docker, persist history and attach the project
    LaunchedEffect(Unit) {
        refreshDocker()
        attachCurrentProject(projectPath)
    }

    // Lazily refresh the panels that need live data when the user opens them
    LaunchedEffect(selectedNav) {
        when (selectedNav) {
            NavItem.Health -> runIo { loadHealth() }
            NavItem.History -> runIo { loadHistory() }
            else -> Unit
        }
    }

    // Periodic live log tailing for the chosen container
    LaunchedEffect(selectedContainerId) {
        val containerId = selectedContainerId ?: return@LaunchedEffect
        while (true) {
            try {
                val raw = withContext(Dispatchers.IO) { client.logs(containerId, 500) }
                if (raw.isNotBlank()) {
                    logEntries = parseLogEntries(raw)
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
                    userName = userName,
                    userEmail = userEmail,
                    onOpenProfile = { showProfile = true },
                    onLogout = { confirmLogout = true }
                )

                // Tab Routing based on Sidebar selection
                when (selectedNav) {
                    NavItem.Dashboard -> {
                        DashboardMainView(
                            containers = containers,
                            selectedContainerId = selectedContainerId,
                            selectedContainerName = selectedContainerName,
                            logEntries = logEntries,
                            selectedFinding = selectedFinding,
                            verification = selectedFinding?.id?.let { verificationsByFinding[it] },
                            canRollback = selectedFinding?.id?.let { it in rolledBackFindings } == false &&
                                (selectedFinding?.patches?.isNotEmpty() ?: false),
                            isBusy = busy,
                            onRefreshDocker = { refreshDocker() },
                            onSelectContainer = { container ->
                                selectContainer(container)
                            },
                            onRunDiagnostics = { runDiagnostics() },
                            onReviewFix = { pendingRepair = it },
                            onVerifyFinding = { verifyFinding(it) },
                            onRollback = { rollbackFinding(it) },
                            onExportReport = { exportReport() }
                        )
                    }

                    NavItem.Health -> {
                        HealthPanel(
                            health = health,
                            isBusy = busy,
                            onRefresh = { refreshDocker() },
                            onRestart = { restartContainer(it) },
                            onStart = { startContainer(it) },
                            onViewLogs = { container ->
                                selectContainer(container)
                                selectedNav = NavItem.LiveLogs
                            },
                            onRunDiagnostics = {
                                selectedNav = NavItem.Diagnosis
                                runDiagnostics()
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
                            logAnalysis = logAnalysis,
                            logAnalysisBusy = logAnalysisBusy,
                            onSelectContainer = { container ->
                                selectContainer(container)
                            },
                            onRefreshAnalysis = { loadLogAnalysis(selectedContainerId) }
                        )
                    }

                    NavItem.Diagnosis -> {
                        DiagnosisFullView(
                            findings = findings,
                            selectedFinding = selectedFinding,
                            verification = selectedFinding?.id?.let { verificationsByFinding[it] },
                            canRollback = (selectedFinding?.id?.let { it in rolledBackFindings } == false) &&
                                (selectedFinding?.patches?.isNotEmpty() ?: false),
                            isBusy = busy,
                            onSelectFinding = { selectedFinding = it },
                            onRun = { runDiagnostics() },
                            onReviewFix = { pendingRepair = it },
                            onVerify = { verifyFinding(it) },
                            onRollback = { rollbackFinding(it) },
                            onExportReport = { exportReport() }
                        )
                    }

                    NavItem.Repairs -> {
                        RepairsFullView(
                            findings = findings,
                            onApply = { pendingRepair = it }
                        )
                    }

                    NavItem.History -> {
                        HistoryPanel(
                            runs = runHistory,
                            verifications = verificationHistory,
                            isBusy = busy,
                            onRefresh = { runIo { loadHistory() } },
                            onOpenRun = { openRun(it) }
                        )
                    }

                    NavItem.Reports -> {
                        ReportsFullView(
                            projectName = projectName,
                            projectPath = projectPath,
                            containersCount = containers.size,
                            findings = findings,
                            recentDiagnostics = recentDiagnostics,
                            onExportReport = { exportReport() }
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

    // Feature 5: the developer approval gate. Nothing is written and no container
    // is touched until this dialog is confirmed.
    pendingRepair?.let { finding ->
        ConfirmRepairDialog(
            finding = finding,
            onDismiss = { pendingRepair = null },
            onConfirm = { restart -> confirmRepair(finding, restart) }
        )
    }

    // Profile settings / logout, opened from the top-bar account menu
    if (showProfile) {
        ProfileDialog(
            userName = userName,
            userEmail = userEmail,
            backendUrl = backendUrl,
            onDismiss = { showProfile = false },
            onLogout = {
                showProfile = false
                confirmLogout = true
            }
        )
    }

    if (confirmLogout) {
        LogoutConfirmDialog(
            userName = userName,
            userEmail = userEmail,
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                onLogout()
            }
        )
    }
}

@Composable
private fun DashboardMainView(
    containers: List<ContainerItem>,
    selectedContainerId: String?,
    selectedContainerName: String,
    logEntries: List<LogEntry>,
    selectedFinding: DiagnosticFinding?,
    verification: VerificationOutcome?,
    canRollback: Boolean,
    isBusy: Boolean,
    onRefreshDocker: () -> Unit,
    onSelectContainer: (ContainerItem) -> Unit,
    onRunDiagnostics: () -> Unit,
    onReviewFix: (DiagnosticFinding) -> Unit,
    onVerifyFinding: (DiagnosticFinding) -> Unit,
    onRollback: (DiagnosticFinding) -> Unit,
    onExportReport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Container health at a glance
        StatCardsRow(
            totalContainers = containers.size,
            runningCount = containers.count { it.isRunning },
            unhealthyCount = containers.count { it.isUnhealthy },
            stoppedCount = containers.count { !it.isRunning }
        )

        // What is running on the left, what is wrong on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: containers and their live output
            Column(
                modifier = Modifier.weight(0.64f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ContainersTable(
                    containers = containers,
                    selectedContainerId = selectedContainerId,
                    onViewLogs = { container ->
                        onSelectContainer(container)
                    },
                    onRefresh = onRefreshDocker
                )

                LiveLogsView(
                    containerName = selectedContainerName,
                    containerId = selectedContainerId,
                    containersList = containers,
                    logEntries = logEntries,
                    onSelectContainer = onSelectContainer
                )
            }

            // Right Column: the diagnosis for the selected finding
            Column(
                modifier = Modifier.weight(0.36f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DiagnosisResultView(
                    finding = selectedFinding,
                    verification = verification,
                    canRollback = canRollback,
                    isBusy = isBusy,
                    onRunAgain = onRunDiagnostics,
                    onReviewAndApplyFix = onReviewFix,
                    onVerify = onVerifyFinding,
                    onRollback = onRollback,
                    onExportReport = onExportReport
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
    logAnalysis: LogAnalysis?,
    logAnalysisBusy: Boolean,
    onSelectContainer: (ContainerItem) -> Unit,
    onRefreshAnalysis: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Container Log Console", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Text(
            "Live output for the selected container, grouped into failure signatures with their evidence.",
            fontSize = 12.sp,
            color = AppColors.TextMuted
        )

        if (logAnalysis != null) {
            LogAnalysisPanel(
                analysis = logAnalysis,
                isLoading = logAnalysisBusy,
                onRefresh = onRefreshAnalysis
            )
        }

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
    verification: VerificationOutcome?,
    canRollback: Boolean,
    isBusy: Boolean,
    onSelectFinding: (DiagnosticFinding) -> Unit,
    onRun: () -> Unit,
    onReviewFix: (DiagnosticFinding) -> Unit,
    onVerify: (DiagnosticFinding) -> Unit,
    onRollback: (DiagnosticFinding) -> Unit,
    onExportReport: () -> Unit
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
                Text("Every finding cites the log lines, configuration and Docker state that produced it.", fontSize = 12.sp, color = AppColors.TextMuted)
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
                verification = verification,
                canRollback = canRollback,
                isBusy = isBusy,
                onRunAgain = onRun,
                onReviewAndApplyFix = onReviewFix,
                onVerify = onVerify,
                onRollback = onRollback,
                onExportReport = onExportReport,
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
        Text(
            "Review the exact diff - or the container action - before anything is written. Nothing is applied until you confirm.",
            fontSize = 12.sp,
            color = AppColors.TextSecondary
        )

        val repairable = findings.filter { it.fixable }
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(finding.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                                Text(
                                    "risk ${finding.risk.lowercase()} · confidence ${finding.confidence}%",
                                    fontSize = 10.sp,
                                    color = AppColors.TextMuted
                                )
                            }
                            Text(finding.repairDescription ?: "", fontSize = 12.sp, color = AppColors.TextSecondary)
                            finding.actions.forEach { action ->
                                Text(
                                    "action: ${action.kind} \u00B7 ${action.target}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppColors.StatusAmber
                                )
                            }
                            // The full before/after diff lives in the approval dialog - one line per
                            // touched file keeps this list scannable.
                            finding.patches.forEach { patch ->
                                Text(
                                    "${patch.mode} \u00B7 ${patch.path}",
                                    fontSize = 11.sp,
                                    color = AppColors.PrimaryBlueHover,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Button(
                                onClick = { onApply(finding) },
                                modifier = Modifier.align(Alignment.End),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Review fix", fontSize = 12.sp)
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
    recentDiagnostics: List<RecentDiagnosticItem>,
    onExportReport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Diagnostics & Environment Report", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Button(onClick = onExportReport, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Markdown report", fontSize = 12.sp)
            }
        }

        Text(
            "The report contains the infrastructure verdict, every root cause with its evidence, the suggested patches and the post-fix verification table.",
            fontSize = 12.sp,
            color = AppColors.TextMuted
        )

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

private fun parseLogLine(raw: String): LogEntry {
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
        message = remaining,
        raw = raw
    )
}

/**
 * Turns the raw Docker log blob into the lines the console renders.
 *
 * Docker prefixes every line with its own timestamp when logs are tailed with
 * timestamps, and emits lines that hold nothing but that timestamp. Those carry
 * no information, so they are dropped here instead of being shown twice (once in
 * the timestamp column and once as the message).
 */
private fun parseLogEntries(raw: String): List<LogEntry> =
    raw.lines()
        .filter { it.isNotBlank() }
        .map { parseLogLine(it) }
        .filter { it.message.isNotBlank() && !TimestampOnlyLine.matches(it.message) }

/** A line that is nothing but an RFC3339 timestamp - Docker's empty-chunk marker. */
private val TimestampOnlyLine = Regex(
    """^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?$"""
)

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
