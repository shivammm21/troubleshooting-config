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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.model.ContainerItem
import com.stackpilot.desktop.model.LogEntry
import com.stackpilot.desktop.model.LogLevel
import com.stackpilot.desktop.theme.AppColors

/** Every control in the log toolbar shares one height, shape and border. */
private val ToolbarHeight = 30.dp

/** One line height for every console column, so timestamps and messages share a baseline. */
private val LogLineHeight = 16.sp

private val TimestampWidth = 120.dp
private val LevelWidth = 40.dp
private val LoggerMaxWidth = 150.dp

/** Which optional console columns the visible buffer actually fills - avoids empty gutters. */
private data class LogColumns(
    val timestamp: Boolean,
    val level: Boolean,
    val logger: Boolean
)

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

    val selectedContainerRunning = containersList.firstOrNull { it.name == containerName }?.isRunning == true

    val filteredLogs = remember(logEntries, searchQuery) {
        if (searchQuery.isBlank()) logEntries
        else logEntries.filter {
            it.message.contains(searchQuery, ignoreCase = true) ||
                    it.logger.contains(searchQuery, ignoreCase = true) ||
                    (it.level?.name?.contains(searchQuery, ignoreCase = true) == true) ||
                    it.raw.contains(searchQuery, ignoreCase = true)
        }
    }

    val columns = remember(filteredLogs) {
        LogColumns(
            timestamp = filteredLogs.any { it.timestamp.isNotBlank() },
            level = filteredLogs.any { it.level != null },
            logger = filteredLogs.any { it.logger.isNotBlank() }
        )
    }

    // Follow the tail only while the reader is already at the bottom, so scrolling
    // up to inspect an older line is not yanked back by the next refresh.
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 1
        }
    }

    LaunchedEffect(filteredLogs.size, isAutoScroll, isPaused) {
        if (isAutoScroll && !isPaused && isAtBottom && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(10.dp)),
        color = AppColors.CardBackground
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Title row: identity + line count on the left, filter on the right ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Live Logs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                if (!containerId.isNullOrBlank() && containerId.length >= 8) {
                    Text(
                        text = containerId.take(8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.TextMuted
                    )
                }

                // How many lines are in view - blue while filtering, amber while paused
                CountPill(
                    text = when {
                        isPaused && searchQuery.isNotBlank() -> "paused · ${filteredLogs.size} of ${logEntries.size}"
                        isPaused -> "paused · ${logEntries.size} lines"
                        searchQuery.isNotBlank() -> "${filteredLogs.size} of ${logEntries.size} lines"
                        else -> "${logEntries.size} lines"
                    },
                    accent = when {
                        isPaused -> AppColors.StatusAmber
                        searchQuery.isNotBlank() -> AppColors.PrimaryBlueHover
                        else -> AppColors.TextSecondary
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Text filter – grows an inline clear affordance only when it holds a query
                Row(
                    modifier = Modifier
                        .width(230.dp)
                        .height(ToolbarHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (searchQuery.isBlank()) AppColors.CardBorder else AppColors.PrimaryBlue.copy(alpha = 0.55f),
                            RoundedCornerShape(8.dp)
                        )
                        .background(AppColors.TerminalBackground.copy(alpha = 0.6f))
                        .padding(start = 9.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AppColors.TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif
                        ),
                        cursorBrush = SolidColor(AppColors.PrimaryBlueHover),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Filter logs",
                                    color = AppColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear filter",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Controls: which container, and how the stream behaves ───────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Container selector
                Box {
                    Row(
                        modifier = Modifier
                            .height(ToolbarHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(8.dp))
                            .background(AppColors.TerminalBackground.copy(alpha = 0.6f))
                            .clickable { isDropdownOpen = true }
                            .padding(start = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (selectedContainerRunning) AppColors.StatusGreen else AppColors.StatusGray)
                        )
                        Text(
                            text = containerName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.TextPrimary,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Select container",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isDropdownOpen,
                        onDismissRequest = { isDropdownOpen = false }
                    ) {
                        if (containersList.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No container available", fontSize = 11.sp, color = AppColors.TextMuted) },
                                onClick = { isDropdownOpen = false }
                            )
                        }
                        containersList.forEach { container ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (container.isRunning) AppColors.StatusGreen else AppColors.StatusGray)
                                    )
                                },
                                text = {
                                    Column {
                                        Text(
                                            text = container.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (container.name == containerName) FontWeight.Bold else FontWeight.SemiBold,
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
                ToolbarChip(
                    label = "Auto-scroll",
                    icon = Icons.Filled.KeyboardArrowDown,
                    active = isAutoScroll,
                    accent = AppColors.PrimaryBlueHover,
                    onClick = { isAutoScroll = !isAutoScroll }
                )

                // Pause / resume toggle
                ToolbarChip(
                    label = if (isPaused) "Resume" else "Pause",
                    icon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    active = isPaused,
                    accent = AppColors.StatusAmber,
                    onClick = { isPaused = !isPaused }
                )

                Spacer(modifier = Modifier.weight(1f))
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank())
                                "No line matches \"$searchQuery\"."
                            else
                                "No log output for '$containerName' yet. Start or execute something in this container to stream its output.",
                            color = AppColors.TextMuted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (searchQuery.isNotBlank()) {
                            ToolbarChip(
                                label = "Clear filter",
                                icon = Icons.Filled.Close,
                                active = true,
                                accent = AppColors.PrimaryBlueHover,
                                onClick = { searchQuery = "" }
                            )
                        }
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
                            LogLineItem(log = log, columns = columns)
                        }
                    }
                }
            }
        }
    }
}

/** Line counter next to the panel title - neutral, blue while filtering, amber while paused. */
@Composable
private fun CountPill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            color = accent
        )
    }
}

@Composable
private fun ToolbarChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val contentColor = if (active) accent else AppColors.TextSecondary

    Row(
        modifier = Modifier
            .height(ToolbarHeight)
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (active) accent.copy(alpha = 0.55f) else AppColors.CardBorder,
                RoundedCornerShape(8.dp)
            )
            .background(if (active) accent.copy(alpha = 0.14f) else AppColors.TerminalBackground.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun LogLineItem(log: LogEntry, columns: LogColumns) {
    val levelColor = when (log.level) {
        LogLevel.ERROR -> AppColors.StatusRed
        LogLevel.WARN  -> AppColors.StatusAmber
        LogLevel.DEBUG -> AppColors.TextMuted
        LogLevel.INFO  -> AppColors.StatusGreen
        null           -> null
    }

    val msgColor = when {
        log.level == LogLevel.ERROR -> AppColors.StatusRed.copy(alpha = 0.95f)
        log.level == LogLevel.WARN  -> AppColors.StatusAmber.copy(alpha = 0.95f)
        log.raw.contains("Exception", ignoreCase = true) ||
        log.raw.contains("Error", ignoreCase = true) ||
        log.raw.contains("FAILED", ignoreCase = true) -> AppColors.StatusRed.copy(alpha = 0.85f)
        else -> AppColors.TextCode
    }

    // Only failures carry the rail and the tint - everything else stays calm
    val railColor = when (log.level) {
        LogLevel.ERROR -> AppColors.StatusRed
        LogLevel.WARN  -> AppColors.StatusAmber
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (railColor == Color.Transparent) Color.Transparent else railColor.copy(alpha = 0.05f)),
        verticalAlignment = Alignment.Top
    ) {
        // 2dp rail: instant visual scanning for the lines that went wrong
        Box(
            modifier = Modifier
                .padding(vertical = 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(railColor)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (columns.timestamp) {
                Text(
                    text = log.timestamp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextMuted,
                    lineHeight = LogLineHeight,
                    maxLines = 1,
                    modifier = Modifier.width(TimestampWidth)
                )
            }

            if (columns.level) {
                Box(modifier = Modifier.width(LevelWidth)) {
                    if (log.level != null && levelColor != null) {
                        if (log.level == LogLevel.ERROR || log.level == LogLevel.WARN) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(levelColor.copy(alpha = 0.16f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = log.level.name,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = levelColor,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Text(
                                text = log.level.name,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = levelColor.copy(alpha = 0.85f),
                                lineHeight = LogLineHeight,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (columns.logger) {
                Text(
                    text = log.logger,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.PrimaryBlueHover.copy(alpha = 0.85f),
                    lineHeight = LogLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = LoggerMaxWidth)
                )
            }

            // Message takes whatever is left and wraps under its own column start
            Text(
                text = log.message.ifBlank { log.raw },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = msgColor,
                lineHeight = LogLineHeight,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
