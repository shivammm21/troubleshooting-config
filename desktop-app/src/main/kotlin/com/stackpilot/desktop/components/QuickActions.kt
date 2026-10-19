package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Science
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
fun QuickActions(
    onRunDiagnostics: () -> Unit,
    onStartLab: () -> Unit,
    onOpenDocs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
        color = AppColors.CardBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Quick Actions",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )

            ActionItemRow(
                icon = Icons.Filled.MedicalServices,
                title = "Run Diagnostics",
                description = "Analyze logs and configuration",
                onClick = onRunDiagnostics
            )

            ActionItemRow(
                icon = Icons.Filled.Science,
                title = "Start Lab Environment",
                description = "Run predefined failure scenarios",
                onClick = onStartLab
            )

            ActionItemRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Open Project Documentation",
                description = "View setup guides and docs",
                onClick = onOpenDocs
            )
        }
    }
}

@Composable
private fun ActionItemRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, AppColors.CardBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AppColors.CardBorder.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AppColors.PrimaryBlueHover,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = AppColors.TextMuted
            )
        }
    }
}
