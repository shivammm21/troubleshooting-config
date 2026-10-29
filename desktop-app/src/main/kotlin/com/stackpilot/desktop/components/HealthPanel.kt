package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.model.ContainerItem
import com.stackpilot.desktop.model.InfraHealth
import com.stackpilot.desktop.theme.AppColors

/**
 * Feature 1 – Infrastructure Health Monitoring panel.
 *
 * <p>Shows the overall verdict, per-container state/health/exit code and the
 * result of the live TCP probes, then lets the developer restart or start a
 * container without leaving the app.
 */
@Composable
fun HealthPanel(
    health: InfraHealth,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onRestart: (ContainerItem) -> Unit,
    onStart: (ContainerItem) -> Unit,
    onViewLogs: (ContainerItem) -> Unit,
    onRunDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when {
        !health.dockerConnected -> AppColors.StatusRed
        health.isUp -> AppColors.StatusGreen
        health.isDegraded -> AppColors.StatusAmber
        else -> AppColors.StatusGray
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Infrastructure Health",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Text(
                    "Docker state, container health and live TCP probes on every published port.",
                    fontSize = 12.sp,
                    color = AppColors.TextMuted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Re-check", fontSize = 12.sp, color = AppColors.TextSecondary)
                }
                Button(
                    onClick = onRunDiagnostics,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryBlue)
                ) {
                    Icon(Icons.Filled.MedicalServices, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Diagnose", fontSize = 12.sp, color = Color.White)
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            color = accent.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            !health.dockerConnected -> Icons.Filled.PowerOff
                            health.isUp -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Warning
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = health.overall,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        text = if (health.dockerConnected) {
                            health.engineName
                        } else {
                            health.dockerError ?: "Docker engine unreachable"
                        },
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    HealthCounter("Containers", health.total, AppColors.TextPrimary)
                    HealthCounter("Running", health.running, AppColors.StatusGreen)
                    HealthCounter("Unhealthy", health.unhealthy, AppColors.StatusRed)
                    HealthCounter("Stopped", health.stopped, AppColors.StatusGray)
                }
            }
        }

        if (health.issues.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
                color = AppColors.CardBackground
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Detected problems", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    health.issues.forEach { issue ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Filled.Circle,
                                null,
                                tint = AppColors.StatusAmber,
                                modifier = Modifier.size(7.dp).padding(top = 3.dp)
                            )
                            Text(issue, fontSize = 11.sp, color = AppColors.TextSecondary, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Containers
            Surface(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
                color = AppColors.CardBackground
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Containers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    if (health.containers.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No container is running on this host.",
                                fontSize = 11.sp,
                                color = AppColors.TextMuted
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(health.containers) { container ->
                                HealthContainerRow(
                                    container = container,
                                    isBusy = isBusy,
                                    onRestart = { onRestart(container) },
                                    onStart = { onStart(container) },
                                    onViewLogs = { onViewLogs(container) }
                                )
                            }
                        }
                    }
                }
            }

            // Probes
            Surface(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
                color = AppColors.CardBackground
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Service availability", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    Text(
                        "TCP connect to 127.0.0.1 on each published host port.",
                        fontSize = 10.sp,
                        color = AppColors.TextMuted
                    )
                    if (health.dependencies.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No published port to probe. Containers without ports are checked by state only.",
                                fontSize = 11.sp,
                                color = AppColors.TextMuted
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(health.dependencies) { dependency ->
                                val reachable = dependency.reachable
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0F172A).copy(alpha = 0.5f))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (reachable) AppColors.StatusGreen else AppColors.StatusRed)
                                        )
                                        Text(
                                            dependency.service,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.TextPrimary
                                        )
                                        Text(
                                            ":${dependency.hostPort}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = AppColors.TextMuted
                                        )
                                        if (reachable && dependency.latencyMs >= 0) {
                                            Text(
                                                "${dependency.latencyMs}ms",
                                                fontSize = 9.sp,
                                                color = AppColors.StatusGreen
                                            )
                                        }
                                    }
                                    Text(
                                        dependency.detail,
                                        fontSize = 10.sp,
                                        color = AppColors.TextSecondary,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthCounter(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = AppColors.TextMuted)
    }
}

@Composable
private fun HealthContainerRow(
    container: ContainerItem,
    isBusy: Boolean,
    onRestart: () -> Unit,
    onStart: () -> Unit,
    onViewLogs: () -> Unit
) {
    val dot = when {
        container.isStopped -> AppColors.StatusGray
        container.isUnhealthy -> AppColors.StatusRed
        container.isRunning -> AppColors.StatusGreen
        else -> AppColors.StatusAmber
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                container.name,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                buildString {
                    append(container.state)
                    if (container.healthState != "none") append(" · ${container.healthState}")
                    container.exitCode?.let { append(" · exit $it") }
                    if (container.restartCount > 0) append(" · ${container.restartCount} restarts")
                    if (container.ports != "-") append(" · ${container.ports}")
                },
                fontSize = 9.5.sp,
                color = AppColors.TextMuted
            )
        }
        RowActions(
            container = container,
            isBusy = isBusy,
            onRestart = onRestart,
            onStart = onStart,
            onViewLogs = onViewLogs
        )
    }
}

@Composable
private fun RowActions(
    container: ContainerItem,
    isBusy: Boolean,
    onRestart: () -> Unit,
    onStart: () -> Unit,
    onViewLogs: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniAction("Logs", Icons.Filled.Terminal, enabled = true, onClick = onViewLogs)
        if (container.isStopped) {
            MiniAction("Start", Icons.Filled.PlayArrow, enabled = !isBusy, onClick = onStart)
        } else {
            MiniAction("Restart", Icons.Filled.RestartAlt, enabled = !isBusy, onClick = onRestart)
        }
    }
}

@Composable
private fun MiniAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
            .background(Color(0xFF0B101D))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) AppColors.TextSecondary else AppColors.TextMuted,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (enabled) AppColors.TextSecondary else AppColors.TextMuted
        )
    }
}

/** Inline spinner used by the busy states of the health panel. */
@Composable
fun BusySpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(14.dp),
        strokeWidth = 1.5.dp,
        color = AppColors.PrimaryBlueHover
    )
}
