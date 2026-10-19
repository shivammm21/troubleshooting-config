package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.theme.AppColors

@Composable
fun TopBar(
    projectName: String,
    projectPath: String,
    onChangeProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Project info: Folder icon + Project Name + Path
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.PrimaryBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Project folder",
                    tint = AppColors.PrimaryBlueHover,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = projectName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = projectPath,
                    fontSize = 11.sp,
                    color = AppColors.TextMuted
                )
            }
        }

        // Change Project Button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, AppColors.CardBorder, RoundedCornerShape(8.dp))
                .background(AppColors.CardBackground.copy(alpha = 0.6f))
                .clickable(onClick = onChangeProject)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = "Change Project",
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Change Project",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextSecondary
            )
        }
    }
}
