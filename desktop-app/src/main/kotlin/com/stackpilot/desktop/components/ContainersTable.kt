package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.stackpilot.desktop.model.ContainerItem
import com.stackpilot.desktop.theme.AppColors

@Composable
fun ContainersTable(
    containers: List<ContainerItem>,
    selectedContainerId: String?,
    onViewLogs: (ContainerItem) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
        color = AppColors.CardBackground
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Containers",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                        .clickable(onClick = onRefresh)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Refresh",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Table Column Headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Name", modifier = Modifier.weight(1.3f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
                Text("Image", modifier = Modifier.weight(1.7f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
                Text("Status", modifier = Modifier.weight(1.1f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
                Text("Health", modifier = Modifier.weight(1.1f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
                Text("Ports", modifier = Modifier.weight(1.1f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
                Text("Actions", modifier = Modifier.weight(1.0f), fontSize = 11.sp, color = AppColors.TextMuted, fontWeight = FontWeight.SemiBold)
            }

            // Divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppColors.CardBorder.copy(alpha = 0.6f))
            )

            // Table Rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (containers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No Docker containers found on this host. Run containers with 'docker run' or 'docker compose up' to inspect them.",
                            fontSize = 11.5.sp,
                            color = AppColors.TextMuted
                        )
                    }
                } else {
                    containers.forEach { container ->
                        ContainerRow(
                            container = container,
                            isSelected = container.id == selectedContainerId || container.name == selectedContainerId,
                            onViewLogs = { onViewLogs(container) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerRow(
    container: ContainerItem,
    isSelected: Boolean,
    onViewLogs: () -> Unit
) {
    val rowBg = if (isSelected) AppColors.PrimaryBlue.copy(alpha = 0.18f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(rowBg)
            .clickable(onClick = onViewLogs)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name with icon
        Row(
            modifier = Modifier.weight(1.3f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (icon, tint) = getContainerIcon(container.name)
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = container.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary
            )
        }

        // Image
        Text(
            text = container.image,
            modifier = Modifier.weight(1.7f),
            fontSize = 11.sp,
            color = AppColors.TextSecondary
        )

        // Status
        Row(
            modifier = Modifier.weight(1.1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val dotColor = when {
                container.status == "Stopped" -> AppColors.StatusGray
                container.isUnhealthy -> AppColors.StatusRed
                container.isRunning -> AppColors.StatusGreen
                else -> AppColors.StatusGray
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = container.status,
                fontSize = 11.sp,
                color = AppColors.TextPrimary
            )
        }

        // Health
        Row(
            modifier = Modifier.weight(1.1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when (container.health) {
                "Healthy" -> {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Healthy",
                        tint = AppColors.StatusGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Healthy",
                        fontSize = 11.sp,
                        color = AppColors.StatusGreen
                    )
                }
                "Unhealthy" -> {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Unhealthy",
                        tint = AppColors.StatusRed,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Unhealthy",
                        fontSize = 11.sp,
                        color = AppColors.StatusRed
                    )
                }
                else -> {
                    Text(
                        text = "-",
                        fontSize = 12.sp,
                        color = AppColors.TextMuted
                    )
                }
            }
        }

        // Ports
        Text(
            text = container.ports,
            modifier = Modifier.weight(1.1f),
            fontSize = 11.sp,
            color = AppColors.TextSecondary
        )

        // Actions: View Logs button
        Box(
            modifier = Modifier.weight(1.0f),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.5f))
                    .clickable(onClick = onViewLogs)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View Logs",
                    fontSize = 10.sp,
                    color = AppColors.TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun getContainerIcon(name: String): Pair<ImageVector, Color> {
    return when {
        name.contains("api") || name.contains("shop") -> Pair(Icons.Filled.Spa, Color(0xFF10B981))
        name.contains("redis") -> Pair(Icons.Filled.Storage, Color(0xFFEF4444))
        name.contains("kafka") -> Pair(Icons.Filled.Hub, Color(0xFF38BDF8))
        name.contains("postgres") -> Pair(Icons.Filled.Dns, Color(0xFF3B82F6))
        name.contains("zookeeper") -> Pair(Icons.Filled.AccountTree, Color(0xFF818CF8))
        else -> Pair(Icons.Filled.Layers, AppColors.PrimaryBlueHover)
    }
}
