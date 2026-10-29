package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.model.DiagnosisTab
import com.stackpilot.desktop.model.DiagnosticFinding
import com.stackpilot.desktop.model.VerificationOutcome
import com.stackpilot.desktop.theme.AppColors

@Composable
fun DiagnosisResultView(
    finding: DiagnosticFinding?,
    verification: VerificationOutcome?,
    canRollback: Boolean,
    isBusy: Boolean,
    onRunAgain: () -> Unit,
    onReviewAndApplyFix: (DiagnosticFinding) -> Unit,
    onVerify: (DiagnosticFinding) -> Unit,
    onRollback: (DiagnosticFinding) -> Unit,
    onExportReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Reset to Explanation tab whenever a different finding is selected.
    var activeTab by remember(finding?.id) { mutableStateOf(DiagnosisTab.Explanation) }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
        color = AppColors.CardBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title & "Run Again" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Diagnosis Result",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeaderAction(
                        icon = Icons.Filled.FileDownload,
                        label = "Report",
                        enabled = !isBusy,
                        onClick = onExportReport
                    )
                    HeaderAction(
                        icon = Icons.Filled.Refresh,
                        label = if (isBusy) "Running..." else "Run Again",
                        enabled = !isBusy,
                        onClick = onRunAgain
                    )
                }
            }

            if (finding == null) {
                // Empty state when no findings yet
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(8.dp)),
                    color = Color(0xFF0F172A).copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.StatusGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No diagnostic issues found",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "All configuration and container health checks passed or no lab project has been indexed yet.",
                            fontSize = 11.sp,
                            color = AppColors.TextMuted,
                            lineHeight = 15.sp
                        )
                    }
                }
            } else {
                // Finding Banner Box
                val isHigh = finding.severity.equals("HIGH", ignoreCase = true) || finding.severity.equals("ERROR", ignoreCase = true)
                val bannerBorder = if (isHigh) AppColors.BadgeRedBorder else AppColors.StatusAmber.copy(alpha = 0.5f)
                val bannerBg = if (isHigh) Color(0xFF22111A) else Color(0xFF241C12)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, bannerBorder, RoundedCornerShape(8.dp)),
                    color = bannerBg
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isHigh) AppColors.StatusRed else AppColors.StatusAmber),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BugReport,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = finding.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.TextPrimary
                                )
                            }

                            // Severity pill badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, bannerBorder, RoundedCornerShape(12.dp))
                                    .background(if (isHigh) AppColors.BadgeRedBg else AppColors.StatusAmberBg)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = finding.severity,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isHigh) AppColors.BadgeRedText else AppColors.StatusAmber
                                )
                            }
                        }

                        Text(
                            text = finding.summary,
                            fontSize = 11.5.sp,
                            color = AppColors.TextSecondary,
                            lineHeight = 16.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetaChip("confidence ${finding.confidence}%", AppColors.PrimaryBlueHover)
                            MetaChip(finding.category, AppColors.TextSecondary)
                            if (finding.risk.isNotBlank()) {
                                MetaChip(
                                    "risk ${finding.risk}",
                                    when (finding.risk.uppercase()) {
                                        "HIGH" -> AppColors.StatusRed
                                        "MEDIUM" -> AppColors.StatusAmber
                                        else -> AppColors.StatusGreen
                                    }
                                )
                            }
                            if (finding.requiresRestart) {
                                MetaChip("needs restart", AppColors.StatusAmber)
                            }
                        }
                    }
                }

                // Segmented Tabs: Explanation, Evidence, Fix Proposal, Verification
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DiagnosisTabButton(
                        title = "Explanation",
                        isSelected = activeTab == DiagnosisTab.Explanation,
                        onClick = { activeTab = DiagnosisTab.Explanation }
                    )
                    DiagnosisTabButton(
                        title = "Evidence",
                        isSelected = activeTab == DiagnosisTab.Evidence,
                        onClick = { activeTab = DiagnosisTab.Evidence }
                    )
                    DiagnosisTabButton(
                        title = "Fix Proposal",
                        isSelected = activeTab == DiagnosisTab.FixProposal,
                        onClick = { activeTab = DiagnosisTab.FixProposal }
                    )
                    DiagnosisTabButton(
                        title = "Verification",
                        isSelected = activeTab == DiagnosisTab.Verification,
                        onClick = { activeTab = DiagnosisTab.Verification }
                    )
                }

                // Tab Content
                when (activeTab) {
                    DiagnosisTab.Explanation -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Root Cause — the diagnostic summary explaining why this is a problem
                            SectionHeader(
                                icon = Icons.Filled.AddCircle,
                                iconTint = AppColors.PrimaryBlueHover,
                                title = "Root Cause"
                            )
                            CodeSnippetBox(text = finding.summary)

                            // Recommended Fix — the repair description, or a generic hint
                            SectionHeader(
                                icon = Icons.Filled.EditNote,
                                iconTint = AppColors.PrimaryBlueHover,
                                title = "Recommended Fix"
                            )
                            val fixText = when {
                                !finding.repairDescription.isNullOrBlank() -> finding.repairDescription
                                finding.patches.isNotEmpty() -> {
                                    val patch = finding.patches.first()
                                    "In ${patch.path}: change  \"${patch.originalSnippet}\"  →  \"${patch.replacementSnippet}\""
                                }
                                else -> "Check the Docker Compose service name and update the application configuration to use it instead of a loopback address."
                            }
                            CodeSnippetBox(text = fixText)

                            if (finding.nextSteps.isNotEmpty()) {
                                SectionHeader(
                                    icon = Icons.Filled.Checklist,
                                    iconTint = AppColors.StatusAmber,
                                    title = "Manual steps if you prefer to fix it yourself"
                                )
                                finding.nextSteps.forEachIndexed { index, step ->
                                    Text(
                                        text = "${index + 1}. $step",
                                        fontSize = 11.sp,
                                        color = AppColors.TextSecondary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    DiagnosisTab.Evidence -> {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (finding.evidence.isEmpty()) {
                                CodeSnippetBox("No direct evidence was collected. Run diagnostics with Docker running and a project indexed to capture live evidence.")
                            } else {
                                finding.evidence.forEach { ev ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                            .background(Color(0xFF0B101D))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Source badge + location
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val (badgeColor, badgeBg) = when (ev.source.uppercase()) {
                                                "LOG"    -> AppColors.StatusAmber to AppColors.StatusAmberBg
                                                "CONFIG" -> AppColors.PrimaryBlueHover to AppColors.PrimaryBlueMuted
                                                "DOCKER" -> AppColors.StatusGreen to AppColors.StatusGreenBg
                                                "PROBE"  -> AppColors.StatusRed to AppColors.BadgeRedBg
                                                "DOC"    -> AppColors.PrimaryBlueHover to AppColors.PrimaryBlueMuted
                                                else     -> AppColors.TextSecondary to AppColors.CardBorder
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(badgeBg)
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = ev.source,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeColor
                                                )
                                            }
                                            Text(
                                                text = ev.location,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AppColors.TextSecondary,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                        // Excerpt value
                                        val isTruncated = ev.excerpt.length >= 400
                                        Text(
                                            text = ev.excerpt + if (isTruncated) " …" else "",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = AppColors.TextCode,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DiagnosisTab.FixProposal -> {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (finding.patches.isEmpty() && finding.actions.isEmpty()) {
                                CodeSnippetBox("No automated patch is available for this finding. Follow the Recommended Fix in the Explanation tab.")
                            } else {
                                if (finding.actions.isNotEmpty()) {
                                    Text(
                                        text = "Container actions (run only when you approve):",
                                        fontSize = 10.sp,
                                        color = AppColors.TextMuted
                                    )
                                    finding.actions.forEach { action ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0B101D))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = null,
                                                tint = AppColors.StatusAmber,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "${action.kind} \u00B7 ${action.target}",
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = AppColors.TextSecondary
                                            )
                                        }
                                    }
                                }
                                finding.patches.forEach { patch ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                            .background(Color(0xFF060A10))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // File path header
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Description,
                                                contentDescription = null,
                                                tint = AppColors.TextMuted,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = patch.path,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AppColors.TextSecondary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        // Before line (red)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0xFF3D0A0A)),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "- ",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.StatusRed,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                            Text(
                                                text = patch.originalSnippet,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = AppColors.StatusRed.copy(alpha = 0.9f),
                                                modifier = Modifier.padding(end = 6.dp, top = 3.dp, bottom = 3.dp)
                                            )
                                        }
                                        // After line (green)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0xFF0A2A1A)),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "+ ",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.StatusGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                            Text(
                                                text = patch.replacementSnippet,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = AppColors.StatusGreen.copy(alpha = 0.9f),
                                                modifier = Modifier.padding(end = 6.dp, top = 3.dp, bottom = 3.dp)
                                            )
                                        }
                                    }
                                }

                                if (finding.requiresRestart) {
                                    Text(
                                        text = "This change only takes effect after the affected container restarts.",
                                        fontSize = 10.sp,
                                        color = AppColors.StatusAmber
                                    )
                                }
                            }
                        }
                    }

                    DiagnosisTab.Verification -> {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionHeader(
                                icon = Icons.Filled.Verified,
                                iconTint = AppColors.PrimaryBlueHover,
                                title = "Verification Status"
                            )

                            if (verification == null) {
                                CodeSnippetBox(
                                    text = "Not verified yet. StackPilot verifies automatically right after an approved fix, " +
                                        "or you can click 'Verify Now' to re-check the current state of every rule and every published port."
                                )
                            } else {
                                val statusAccent = when {
                                    verification.resolved -> AppColors.StatusGreen
                                    verification.pending -> AppColors.StatusAmber
                                    verification.status.equals("NO_FIX", ignoreCase = true) -> AppColors.StatusGray
                                    else -> AppColors.StatusRed
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, statusAccent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                            .background(statusAccent.copy(alpha = 0.14f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = verification.status.replace('_', ' '),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusAccent
                                        )
                                    }
                                    if (verification.checkedAt.isNotBlank()) {
                                        Text(
                                            text = "checked " + prettyTime(verification.checkedAt),
                                            fontSize = 9.sp,
                                            color = AppColors.TextMuted
                                        )
                                    }
                                }

                                CodeSnippetBox(text = verification.summary)

                                if (verification.runtimeChecks.isNotEmpty()) {
                                    SectionHeader(
                                        icon = Icons.Filled.CheckCircle,
                                        iconTint = AppColors.StatusGreen,
                                        title = "Runtime checks"
                                    )
                                    verification.runtimeChecks.forEach { check ->
                                        Text(
                                            text = "\u2022 " + check,
                                            fontSize = 10.5.sp,
                                            color = AppColors.TextSecondary,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { onVerify(finding) },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Verify Now",
                                    fontSize = 12.sp,
                                    color = AppColors.PrimaryBlueHover
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Big Call to action: Review and Apply Fix (developer approval gate)
                if (finding.fixable) {
                    Button(
                        onClick = { onReviewAndApplyFix(finding) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.PrimaryBlue
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Build,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Review and Apply Fix",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No automated patch for this finding. Follow the manual steps in the Explanation tab.",
                        fontSize = 10.sp,
                        color = AppColors.TextMuted,
                        lineHeight = 14.sp
                    )
                }

                // Roll back a previously applied fix from the on-disk backups
                if (canRollback) {
                    OutlinedButton(
                        onClick = { onRollback(finding) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Undo,
                                contentDescription = null,
                                tint = AppColors.StatusAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Roll back the applied fix",
                                fontSize = 12.sp,
                                color = AppColors.StatusAmber
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosisTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) AppColors.PrimaryBlue else Color(0xFF0F172A).copy(alpha = 0.5f)
    val textCol = if (isSelected) Color.White else AppColors.TextSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = textCol
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) AppColors.TextSecondary else AppColors.TextMuted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}

/** Small metadata pill used in the finding banner. */
@Composable
private fun MetaChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun CodeSnippetBox(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp)),
        color = Color(0xFF0B101D)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(10.dp),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = AppColors.TextSecondary,
            lineHeight = 15.sp
        )
    }
}
