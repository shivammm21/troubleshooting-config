package com.stackpilot.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.model.ContainerItem
import com.stackpilot.desktop.model.LogEntry
import com.stackpilot.desktop.model.LogLevel
import com.stackpilot.desktop.theme.AppColors

@Composable
fun LiveLogsView(
    containerName: String,
    containerId: String? = null,
    containersList: List<ContainerItem>,
    logEntries: List<LogEntry>,
    onSelectContainer: (ContainerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isAutoScroll by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isDropdownOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val filteredLogs = remember(logEntries, searchQuery) {
        if (searchQuery.isBlank()) logEntries
        else logEntries.filter {
            it.message.contains(searchQuery, ignoreCase = true) ||
                    it.logger.contains(searchQuery, ignoreCase = true) ||
                    (it.level?.name?.contains(searchQuery, ignoreCase = true) == true) ||
                    it.raw.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(filteredLogs.size, isAutoScroll, isPaused) {
        if (isAutoScroll && !isPaused && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
        color = AppColors.CardBackground
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header bar with controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Live Logs – $containerName",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    if (!containerId.isNullOrBlank() && containerId.length >= 8) {
                        Text(
                            text = "(${containerId.take(12)})",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppColors.TextMuted
                        )
                    }
                }

                // Controls: container dropdown, auto-scroll, pause, clear, search
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Container selector dropdown
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                                .clickable { isDropdownOpen = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = containerName,
                                fontSize = 11.sp,
                                color = AppColors.TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Select Container",
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isDropdownOpen,
                            onDismissRequest = { isDropdownOpen = false }
                        ) {
                            containersList.forEach { container ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = container.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AppColors.TextPrimary
                                            )
                                            Text(
                                                text = "${container.image} · ${container.status}",
                                                fontSize = 10.sp,
                                                color = AppColors.TextMuted
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectContainer(container)
                                        isDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Auto-scroll toggle
                    Row(
                        modifier = Modifier
                            .clickable { isAutoScroll = !isAutoScroll }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isAutoScroll) AppColors.PrimaryBlue else AppColors.TextMuted)
                        )
                        Text(
                            text = "Auto-scroll",
                            fontSize = 11.sp,
                            color = if (isAutoScroll) AppColors.TextPrimary else AppColors.TextMuted
                        )
                    }

                    // Pause button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                            .clickable { isPaused = !isPaused }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isPaused) "Resume" else "Pause",
                            fontSize = 11.sp,
                            color = if (isPaused) AppColors.StatusAmber else AppColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Clear button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                            .clickable { searchQuery = "" }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Clear Filter",
                            fontSize = 11.sp,
                            color = AppColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Search box
                    Row(
                        modifier = Modifier
                            .width(140.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = AppColors.TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(
                                color = AppColors.TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif
                            ),
                            cursorBrush = SolidColor(AppColors.PrimaryBlueHover),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search logs...",
                                        color = AppColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Terminal log box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF141C2E), RoundedCornerShape(6.dp)),
                color = AppColors.TerminalBackground
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "(No log output for container '$containerName'. Start or execute operations in this container to stream live logs.)",
                            color = AppColors.TextMuted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredLogs) { log ->
                            LogLineItem(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLineItem(log: LogEntry) {
    val levelColor = when (log.level) {
        LogLevel.ERROR -> AppColors.StatusRed
        LogLevel.WARN  -> AppColors.StatusAmber
        LogLevel.DEBUG -> AppColors.TextMuted
        LogLevel.INFO  -> AppColors.StatusGreen
        null           -> null
    }

    // Determine message text color
    val msgColor = when {
        log.level == LogLevel.ERROR -> AppColors.StatusRed.copy(alpha = 0.95f)
        log.level == LogLevel.WARN  -> AppColors.StatusAmber.copy(alpha = 0.95f)
        log.raw.contains("Exception", ignoreCase = true) ||
        log.raw.contains("Error", ignoreCase = true) ||
        log.raw.contains("FAILED", ignoreCase = true) -> AppColors.StatusRed.copy(alpha = 0.85f)
        else -> AppColors.TextCode
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Timestamp – fixed width column
        if (log.timestamp.isNotBlank()) {
            Text(
                text = log.timestamp,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = AppColors.TextMuted,
                modifier = Modifier.width(138.dp)   // "YYYY-MM-DD HH:MM:SS" = 19 chars
            )
        } else {
            Spacer(modifier = Modifier.width(138.dp))
        }

        // Level badge – fixed width column
        if (log.level != null && levelColor != null) {
            Text(
                text = log.level.name.padEnd(5),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = levelColor,
                modifier = Modifier.width(42.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(42.dp))
        }

        // Logger – shrinks if too long
        if (log.logger.isNotBlank()) {
            Text(
                text = log.logger,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF60A5FA).copy(alpha = 0.8f),
                modifier = Modifier.widthIn(max = 200.dp),
                maxLines = 1,
                softWrap = false
            )
        }

        // Message – takes remaining space, wraps naturally
        Text(
            text = log.message.ifBlank { log.raw },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = msgColor,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
