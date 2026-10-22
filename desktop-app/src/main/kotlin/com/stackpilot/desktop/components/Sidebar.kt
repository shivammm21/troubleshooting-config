package com.stackpilot.desktop.components

import androidx.compose.foundation.background
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
import com.stackpilot.desktop.model.NavItem
import com.stackpilot.desktop.theme.AppColors

@Composable
fun Sidebar(
    selectedItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
    dockerConnected: Boolean,
    backendPort: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(AppColors.SidebarBackground)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section: Logo + Navigation list
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // Brand Logo & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Show the real PNG logo; fall back to the icon if the resource
                // is missing (e.g. during a run before assets are bundled).
                val logoBitmap = rememberLogoBitmap()
                if (logoBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = logoBitmap,
                        contentDescription = "StackPilot logo",
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppColors.PrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NearMe,
                            contentDescription = "StackPilot Logo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "StackPilot",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "Diagnose · Understand · Fix",
                        fontSize = 10.sp,
                        color = AppColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Navigation Items
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NavItemRow(
                    item = NavItem.Dashboard,
                    icon = Icons.Filled.Dashboard,
                    isSelected = selectedItem == NavItem.Dashboard,
                    onClick = { onItemSelected(NavItem.Dashboard) }
                )
                NavItemRow(
                    item = NavItem.Project,
                    icon = Icons.Filled.Folder,
                    isSelected = selectedItem == NavItem.Project,
                    onClick = { onItemSelected(NavItem.Project) }
                )
                NavItemRow(
                    item = NavItem.Containers,
                    icon = Icons.Filled.Layers,
                    isSelected = selectedItem == NavItem.Containers,
                    onClick = { onItemSelected(NavItem.Containers) }
                )
                NavItemRow(
                    item = NavItem.LiveLogs,
                    icon = Icons.Filled.Terminal,
                    isSelected = selectedItem == NavItem.LiveLogs,
                    onClick = { onItemSelected(NavItem.LiveLogs) }
                )
                NavItemRow(
                    item = NavItem.Diagnosis,
                    icon = Icons.Filled.MedicalServices,
                    isSelected = selectedItem == NavItem.Diagnosis,
                    onClick = { onItemSelected(NavItem.Diagnosis) }
                )
                NavItemRow(
                    item = NavItem.Repairs,
                    icon = Icons.Filled.Build,
                    isSelected = selectedItem == NavItem.Repairs,
                    onClick = { onItemSelected(NavItem.Repairs) }
                )
                NavItemRow(
                    item = NavItem.Reports,
                    icon = Icons.Filled.Assessment,
                    isSelected = selectedItem == NavItem.Reports,
                    onClick = { onItemSelected(NavItem.Reports) }
                )
                NavItemRow(
                    item = NavItem.Settings,
                    icon = Icons.Filled.Settings,
                    isSelected = selectedItem == NavItem.Settings,
                    onClick = { onItemSelected(NavItem.Settings) }
                )
            }
        }

        // Bottom section: Docker Status, Backend Status, Version
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Docker Status Card
            SidebarStatusCard(
                title = "Docker Status",
                status = if (dockerConnected) "Connected" else "Offline",
                subtitle = if (dockerConnected) "Docker Engine is running" else "Docker daemon stopped",
                isOnline = dockerConnected
            )

            // Backend Status Card
            SidebarStatusCard(
                title = "Backend Status",
                status = "Running",
                subtitle = "Spring Boot started\nPort: $backendPort",
                isOnline = true
            )

            // App Version
            Text(
                text = "StackPilot v0.1.0",
                fontSize = 11.sp,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun NavItemRow(
    item: NavItem,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) AppColors.PrimaryBlue else Color.Transparent
    val contentColor = if (isSelected) Color.White else AppColors.TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = item.title,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun SidebarStatusCard(
    title: String,
    status: String,
    subtitle: String,
    isOnline: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0F172A).copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = AppColors.TextMuted
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) AppColors.StatusGreen else AppColors.StatusRed)
                )
                Text(
                    text = status,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
            }
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = AppColors.TextMuted,
                lineHeight = 13.sp
            )
        }
    }
}
