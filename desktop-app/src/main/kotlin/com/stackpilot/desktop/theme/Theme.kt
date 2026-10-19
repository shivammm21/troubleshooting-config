package com.stackpilot.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val Background = Color(0xFF090D16)
    val SidebarBackground = Color(0xFF0D121F)
    val CardBackground = Color(0xFF131B2E)
    val CardBackgroundElevated = Color(0xFF182238)
    val CardBorder = Color(0xFF1E2B45)
    val TerminalBackground = Color(0xFF060911)

    // Primary Accents
    val PrimaryBlue = Color(0xFF2563EB)
    val PrimaryBlueHover = Color(0xFF3B82F6)
    val PrimaryBlueMuted = Color(0x262563EB)

    // Status Colors
    val StatusGreen = Color(0xFF10B981)
    val StatusGreenBg = Color(0x1F10B981)
    val StatusGreenBorder = Color(0x4D10B981)

    val StatusRed = Color(0xFFEF4444)
    val StatusRedBg = Color(0x1FEF4444)
    val StatusRedBorder = Color(0x4DEF4444)

    val StatusAmber = Color(0xFFF59E0B)
    val StatusAmberBg = Color(0x1FF59E0B)

    val StatusGray = Color(0xFF64748B)
    val StatusGrayBg = Color(0x1F64748B)

    // Typography & Muted
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    val TextCode = Color(0xFFE2E8F0)

    // Badges & Accents
    val BadgeRedBg = Color(0x33EF4444)
    val BadgeRedText = Color(0xFFF87171)
    val BadgeRedBorder = Color(0x80EF4444)

    val BadgeBlueBg = Color(0x333B82F6)
    val BadgeBlueText = Color(0xFF60A5FA)
}

val DarkThemeColorScheme = darkColorScheme(
    primary = AppColors.PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimaryBlueMuted,
    onPrimaryContainer = AppColors.PrimaryBlueHover,
    surface = AppColors.CardBackground,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.CardBackgroundElevated,
    onSurfaceVariant = AppColors.TextSecondary,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    outline = AppColors.CardBorder,
    error = AppColors.StatusRed,
    onError = Color.White
)

@Composable
fun StackPilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkThemeColorScheme,
        content = content
    )
}
