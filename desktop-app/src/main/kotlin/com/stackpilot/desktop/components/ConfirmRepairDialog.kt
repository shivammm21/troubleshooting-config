package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.model.DiagnosticFinding
import com.stackpilot.desktop.theme.AppColors

/**
 * Feature 5 – developer approval gate.
 *
 * <p>No file is written and no container is touched until this dialog is
 * confirmed. It shows the exact diff, the risk level and every non-file action,
 * and lets the developer decide whether the containers may be restarted.
 */
@Composable
fun ConfirmRepairDialog(
    finding: DiagnosticFinding,
    onDismiss: () -> Unit,
    onConfirm: (restart: Boolean) -> Unit
) {
    var restart by remember { mutableStateOf(finding.requiresRestart || finding.actions.isNotEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.CardBackground,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = {
            Text(
                text = "Approve this fix?",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(finding.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Text(
                    text = finding.repairDescription
                        ?: "Apply the change below. StackPilot keeps a .stackpilot.bak backup so you can roll back.",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    lineHeight = 15.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Pill("Risk ${finding.risk}", if (finding.risk.equals("HIGH", true)) AppColors.StatusRed else AppColors.StatusAmber)
                    Pill("Confidence ${finding.confidence}%", AppColors.PrimaryBlueHover)
                    Pill(if (finding.risk.equals("LOW", true)) "Reversible" else "Review carefully",
                        if (finding.risk.equals("LOW", true)) AppColors.StatusGreen else AppColors.StatusAmber)
                }

                finding.patches.forEach { patch ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
                            .background(Color(0xFF060A10))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${patch.mode} · ${patch.path}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppColors.PrimaryBlueHover
                        )
                        if (patch.mode == "APPEND_LINE") {
                            Text(
                                text = "+ ${patch.replacementSnippet}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AppColors.StatusGreen
                            )
                        } else if (patch.mode == "CREATE_FILE") {
                            Text(
                                text = "+ (create file with ${patch.replacementSnippet.length} chars)",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AppColors.StatusGreen
                            )
                        } else {
                            Text(
                                text = "- ${patch.originalSnippet}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AppColors.StatusRed
                            )
                            Text(
                                text = "+ ${patch.replacementSnippet}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AppColors.StatusGreen
                            )
                        }
                    }
                }

                finding.actions.forEach { action ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestartAlt,
                            contentDescription = null,
                            tint = AppColors.StatusAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${action.kind} · ${action.target}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppColors.TextSecondary
                        )
                    }
                }

                if (finding.patches.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(checked = restart, onCheckedChange = { restart = it })
                        Text(
                            text = "Restart the affected container(s) after writing the change",
                            fontSize = 11.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                    if (!restart) {
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
                                text = "Without a restart the new value is on disk but the running process keeps the old one.",
                                fontSize = 10.sp,
                                color = AppColors.StatusAmber
                            )
                        }
                    }
                }

                Text(
                    text = "A .stackpilot.bak copy is written next to every modified file, and verification runs immediately after the fix.",
                    fontSize = 10.sp,
                    color = AppColors.TextMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(restart) }) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = AppColors.PrimaryBlueHover,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Apply approved fix", color = AppColors.PrimaryBlueHover, fontSize = 12.sp)
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
private fun Pill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
