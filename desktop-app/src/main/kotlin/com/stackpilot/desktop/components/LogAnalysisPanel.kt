package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import com.stackpilot.desktop.model.LogAnalysis
import com.stackpilot.desktop.model.SignatureItem
import com.stackpilot.desktop.theme.AppColors

/**
 * Feature 2 – Log Analysis and Error Detection.
 *
 * <p>Replaces "here is a wall of text" with grouped failure signatures: how many
 * times each distinct failure happened, which family it belongs to and one sample
 * line with its position, so the developer can jump straight to the first hit.
 */
@Composable
fun LogAnalysisPanel(
    analysis: LogAnalysis,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
        color = AppColors.CardBackground
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = AppColors.PrimaryBlueHover,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Log analysis",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    if (analysis.containerName.isNotBlank()) {
                        Text(
                            text = analysis.containerName,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppColors.TextMuted
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoading) BusySpinner()
                    MiniTextAction("Re-analyse", onRefresh)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Lines", analysis.totalLines.toString(), AppColors.TextSecondary)
                Metric("Errors", analysis.errorLines.toString(), AppColors.StatusRed)
                Metric("Warnings", analysis.warnLines.toString(), AppColors.StatusAmber)
                Metric("Info", analysis.infoLines.toString(), AppColors.TextMuted)
            }

            if (analysis.categories.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    analysis.categories.take(6).forEach { category ->
                        CategoryChip(category)
                    }
                }
            }

            if (analysis.signatures.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0B101D))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "No failure signature in the last ${analysis.totalLines} log line(s).",
                        fontSize = 11.sp,
                        color = AppColors.StatusGreen
                    )
                    Text(
                        text = "StackPilot keeps watching the tail; run diagnostics for configuration-level checks.",
                        fontSize = 10.sp,
                        color = AppColors.TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(analysis.signatures) { signature ->
                        SignatureRow(signature)
                    }
                }
            }

            if (analysis.exceptionTypes.isNotEmpty()) {
                Text(
                    text = "Exceptions: " + analysis.exceptionTypes.take(4).joinToString(", "),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun SignatureRow(signature: SignatureItem) {
    val accent = if (signature.severity == "ERROR") AppColors.StatusRed else AppColors.StatusAmber
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
            .background(Color(0xFF0B101D))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${signature.count}\u00D7",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            CategoryChip(signature.category)
            Spacer(Modifier.weight(1f))
            Text(
                text = "line ${signature.line}",
                fontSize = 9.sp,
                color = AppColors.TextMuted
            )
        }
        Text(
            text = signature.sample,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            color = AppColors.TextCode,
            lineHeight = 14.sp,
            maxLines = 3
        )
    }
}

@Composable
private fun CategoryChip(category: String) {
    val color = when (category.uppercase()) {
        "OOM", "PORT", "CRASH" -> AppColors.StatusRed
        "CONNECTION", "DNS", "AUTH" -> AppColors.StatusAmber
        "CONFIG", "MIGRATION" -> AppColors.PrimaryBlueHover
        else -> AppColors.TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = category,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 8.5.sp, color = AppColors.TextMuted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun MiniTextAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = label,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(11.dp)
        )
        Text(label, fontSize = 9.sp, color = AppColors.TextSecondary)
    }
}

