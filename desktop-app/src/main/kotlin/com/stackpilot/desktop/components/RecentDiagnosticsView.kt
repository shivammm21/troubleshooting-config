package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.model.RecentDiagnosticItem
import com.stackpilot.desktop.theme.AppColors

@Composable
fun RecentDiagnosticsView(
    items: List<RecentDiagnosticItem>,
    onViewAll: () -> Unit,
    onSelectItem: (RecentDiagnosticItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
        color = AppColors.CardBackground
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent Diagnostics",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                        .clickable(onClick = onViewAll)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View All",
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Table Columns
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Time", modifier = Modifier.weight(1.3f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
                Text("Issue", modifier = Modifier.weight(2.0f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
                Text("Service", modifier = Modifier.weight(1.2f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
                Text("Status", modifier = Modifier.weight(1.2f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppColors.CardBorder.copy(alpha = 0.5f))
            )

            // Rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No diagnostic events recorded yet. Click 'Run Diagnostics' to evaluate the environment.",
                            fontSize = 11.5.sp,
                            color = AppColors.TextMuted
                        )
                    }
                } else {
                    items.forEach { item ->
                        RecentDiagnosticRow(
                            item = item,
                            onClick = { onSelectItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentDiagnosticRow(
    item: RecentDiagnosticItem,
    onClick: () -> Unit
) {
    val statusDotColor = when (item.statusColor) {
        "red" -> AppColors.StatusRed
        "green" -> AppColors.StatusGreen
        "yellow" -> AppColors.StatusAmber
        else -> AppColors.StatusGray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.time,
            modifier = Modifier.weight(1.3f),
            fontSize = 11.sp,
            color = AppColors.TextSecondary
        )

        Text(
            text = item.issue,
            modifier = Modifier.weight(2.0f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary
        )

        Text(
            text = item.service,
            modifier = Modifier.weight(1.2f),
            fontSize = 11.sp,
            color = AppColors.TextSecondary
        )

        Row(
            modifier = Modifier.weight(1.2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )
            Text(
                text = item.status,
                fontSize = 11.sp,
                color = statusDotColor
            )
        }
    }
}
