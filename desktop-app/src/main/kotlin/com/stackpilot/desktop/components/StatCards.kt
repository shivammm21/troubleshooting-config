package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.theme.AppColors

@Composable
fun StatCardsRow(
    totalContainers: Int = 0,
    runningCount: Int = 0,
    unhealthyCount: Int = 0,
    stoppedCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val healthyCount = maxOf(0, totalContainers - unhealthyCount - stoppedCount)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card 1: Containers
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Layers,
            iconTint = Color.White,
            iconBg = AppColors.PrimaryBlue,
            count = totalContainers.toString(),
            title = "Containers",
            subtitle = if (totalContainers > 0) "$runningCount running, $unhealthyCount unhealthy" else "No containers",
            subtitleColor = AppColors.BadgeBlueText,
            cardBg = AppColors.CardBackground,
            borderColor = AppColors.CardBorder
        )

        // Card 2: Healthy
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Check,
            iconTint = Color.White,
            iconBg = AppColors.StatusGreen,
            count = healthyCount.toString(),
            title = "Healthy",
            subtitle = null,
            subtitleColor = AppColors.TextMuted,
            cardBg = Color(0xFF0F201E),
            borderColor = AppColors.StatusGreen.copy(alpha = 0.25f)
        )

        // Card 3: Unhealthy
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.PriorityHigh,
            iconTint = Color.White,
            iconBg = AppColors.StatusRed,
            count = unhealthyCount.toString(),
            title = "Unhealthy",
            subtitle = null,
            subtitleColor = AppColors.TextMuted,
            cardBg = Color(0xFF22111A),
            borderColor = AppColors.StatusRed.copy(alpha = 0.25f)
        )

        // Card 4: Stopped
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Stop,
            iconTint = Color.White,
            iconBg = AppColors.StatusGray,
            count = stoppedCount.toString(),
            title = "Stopped",
            subtitle = null,
            subtitleColor = AppColors.TextMuted,
            cardBg = AppColors.CardBackground,
            borderColor = AppColors.CardBorder
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    count: String,
    title: String,
    subtitle: String?,
    subtitleColor: Color,
    cardBg: Color,
    borderColor: Color
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
        color = cardBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(verticalArrangement = Arrangement.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = count,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                }
            }
        }
    }
}
