package com.stackpilot.desktop.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.theme.AppColors

/**
 * Account panel opened from the top-bar profile menu.
 *
 * Shows who is signed in (name, email), which local backend session is serving
 * the app, and the two working actions: close, or sign out of this device
 * (which routes through [LogoutConfirmDialog]).
 */
@Composable
fun ProfileDialog(
    userName: String,
    userEmail: String,
    backendUrl: String,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.CardBackground,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = {
            Text(
                text = "Profile settings",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Identity
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileAvatar(userName = userName, userEmail = userEmail, size = 44.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = displayNameOf(userName, userEmail),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = userEmail.ifBlank { "No email on this session" },
                            fontSize = 11.sp,
                            color = AppColors.TextMuted
                        )
                    }
                }

                HorizontalDivider(color = AppColors.CardBorder)

                SectionLabel("Account")
                InfoRow("Name", userName.ifBlank { "Not provided" })
                InfoRow("Email", userEmail.ifBlank { "Not provided" })
                InfoRow("Sign-in", "Verified session on this device")

                HorizontalDivider(color = AppColors.CardBorder)

                SectionLabel("Session")
                InfoRow("Backend", backendUrl.ifBlank { "Starting…" }, mono = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AppColors.StatusGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Connected — nothing leaves this machine",
                        fontSize = 10.sp,
                        color = AppColors.StatusGreen
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = null,
                    tint = AppColors.StatusRed,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Log out", color = AppColors.StatusRed, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AppColors.TextSecondary, fontSize = 12.sp)
            }
        }
    )
}

/**
 * Final step before signing out — the account menu and the profile panel both
 * land here so a stray click can never drop the session by accident.
 */
@Composable
fun LogoutConfirmDialog(
    userName: String,
    userEmail: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.CardBackground,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = {
            Text(
                text = "Log out of StackPilot?",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You are signed in as ${userEmail.ifBlank { displayNameOf(userName, userEmail) }}.",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    lineHeight = 16.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = AppColors.StatusAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "The saved session on this device is deleted and the local backend stops. " +
                            "Your diagnostic history stays on disk.",
                        fontSize = 10.sp,
                        color = AppColors.TextMuted,
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = null,
                    tint = AppColors.StatusRed,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Yes, log out", color = AppColors.StatusRed, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppColors.TextSecondary, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.TextMuted,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = AppColors.TextMuted)
        Text(
            text = value,
            fontSize = 11.sp,
            color = AppColors.TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
            fontWeight = FontWeight.Medium
        )
    }
}
