package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.theme.AppColors

@Composable
fun TopBar(
    projectName: String,
    projectPath: String,
    userName: String,
    userEmail: String,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

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

        // Profile menu — replaces the old "Change Project" button
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp))
                    .background(AppColors.CardBackground.copy(alpha = 0.6f))
                    .clickable { menuOpen = true }
                    .padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileAvatar(userName = userName, userEmail = userEmail, size = 26.dp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = displayNameOf(userName, userEmail),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = userEmail.ifBlank { "Signed in" },
                        fontSize = 10.sp,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Account menu",
                    tint = AppColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier
                    .width(230.dp)
                    .background(AppColors.CardBackground)
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = displayNameOf(userName, userEmail),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = userEmail.ifBlank { "No email on this session" },
                        fontSize = 10.sp,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = AppColors.CardBorder)
                DropdownMenuItem(
                    text = { Text("Profile settings", fontSize = 12.sp, color = AppColors.TextSecondary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = AppColors.PrimaryBlueHover,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onOpenProfile()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Log out", fontSize = 12.sp, color = AppColors.StatusRed) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = null,
                            tint = AppColors.StatusRed,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onLogout()
                    }
                )
            }
        }
    }
}

/** Circular avatar showing the user's initials. */
@Composable
fun ProfileAvatar(
    userName: String,
    userEmail: String,
    size: Dp = 28.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(AppColors.PrimaryBlue.copy(alpha = 0.25f))
            .border(1.dp, AppColors.PrimaryBlueHover.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialsOf(userName, userEmail),
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.PrimaryBlueHover
        )
    }
}

/** Full name when we have one, otherwise the local part of the email. */
fun displayNameOf(userName: String, userEmail: String): String =
    userName.trim().ifBlank {
        userEmail.substringBefore('@').ifBlank { "StackPilot user" }
    }

/** First letters of the first two name parts, e.g. "Shivam Thorat" -> "ST". */
fun initialsOf(userName: String, userEmail: String): String {
    val source = userName.trim().ifBlank { userEmail.substringBefore('@') }
    val parts = source.split(' ', '.', '_', '-', '@').filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
