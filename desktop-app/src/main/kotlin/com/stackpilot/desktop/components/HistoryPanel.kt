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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
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
import com.stackpilot.desktop.model.RunHistoryItem
import com.stackpilot.desktop.model.VerificationRecordItem
import com.stackpilot.desktop.theme.AppColors

/**
 * Persisted history: previous diagnostic runs plus every verification verdict.
 *
 * <p>Because the backend stores this in {@code ~/.stackpilot/state.json}, closing
 * the app no longer loses the troubleshooting trail.
 */
@Composable
fun HistoryPanel(
    runs: List<RunHistoryItem>,
    verifications: List<VerificationRecordItem>,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onOpenRun: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text("Diagnostic History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text(
                    "Every run and every verification verdict is persisted locally, so you can reopen an earlier analysis.",
                    fontSize = 12.sp,
                    color = AppColors.TextMuted
                )
            }
            OutlinedButton(onClick = onRefresh, enabled = !isBusy, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Filled.Refresh, null, tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reload", fontSize = 12.sp, color = AppColors.TextSecondary)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
                color = AppColors.CardBackground
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(Icons.Filled.History, "Runs (${runs.size})")
                    if (runs.isEmpty()) {
                        EmptyHint("No diagnostic run stored yet. Run diagnostics to build a history.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(runs) { run -> RunRow(run) { onOpenRun(run.id) } }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
                color = AppColors.CardBackground
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(Icons.Filled.CheckCircle, "Verifications (${verifications.size})")
                    if (verifications.isEmpty()) {
                        EmptyHint("No verification yet. Apply a fix and StackPilot verifies it automatically.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(verifications) { record -> VerificationRow(record) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunRow(run: RunHistoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = prettyTime(run.createdAt),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                text = buildString {
                    append("${run.findings} finding(s)")
                    if (run.errors > 0) append(" · ${run.errors} error(s)")
                    if (run.repairable > 0) append(" · ${run.repairable} fixable")
                    if (run.projectId != null) append(" · project ${run.projectId.take(8)}")
                },
                fontSize = 9.5.sp,
                color = AppColors.TextMuted
            )
        }
        Text(
            text = "open",
            fontSize = 9.sp,
            color = AppColors.PrimaryBlueHover
        )
    }
}

@Composable
private fun VerificationRow(record: VerificationRecordItem) {
    val color = when {
        record.resolved -> AppColors.StatusGreen
        record.status.equals("CONFIG_FIXED_RUNTIME_PENDING", true) -> AppColors.StatusAmber
        record.status.equals("NO_FIX", true) -> AppColors.StatusGray
        else -> AppColors.StatusRed
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.07f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(
                text = record.status,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.weight(1f))
            Text(prettyTime(record.at), fontSize = 9.sp, color = AppColors.TextMuted)
        }
        Text(
            text = record.ruleId,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = AppColors.TextSecondary
        )
        Text(
            text = record.summary,
            fontSize = 10.sp,
            color = AppColors.TextSecondary,
            lineHeight = 14.sp,
            maxLines = 4
        )
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = AppColors.PrimaryBlueHover, modifier = Modifier.size(14.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 11.sp, color = AppColors.TextMuted)
    }
}

/** "2026-10-29T09:15:03.123Z" -> "2026-10-29 09:15". */
internal fun prettyTime(value: String): String {
    if (value.isBlank()) return "-"
    val cleaned = value.replace('T', ' ')
    return if (cleaned.length >= 16) cleaned.substring(0, 16) else cleaned
}
