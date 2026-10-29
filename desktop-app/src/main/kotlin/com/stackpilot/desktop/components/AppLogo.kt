package com.stackpilot.desktop.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as SkiaImage

// ─── Internal helpers ────────────────────────────────────────────────────────

private fun loadResourceBytes(name: String): ByteArray? = try {
    Thread.currentThread()
        .contextClassLoader
        ?.getResourceAsStream(name)
        ?.readBytes()
        ?: AppLogo::class.java.classLoader
            .getResourceAsStream(name)
            ?.readBytes()
} catch (_: Exception) { null }

// Sentinel object used as a classloader anchor above.
private object AppLogo

// ─── White logo (for dark backgrounds – sidebar) ─────────────────────────────

@Composable
fun rememberLogoBitmap(): ImageBitmap? = remember {
    try {
        loadResourceBytes("drawable/logo-white.png")
            ?.let { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }
    } catch (_: Exception) { null }
}

fun loadLogoBytes(): ByteArray? = loadResourceBytes("drawable/logo-white.png")

// ─── Black logo (for light backgrounds – startup screen) ─────────────────────

@Composable
fun rememberBlackLogoBitmap(): ImageBitmap? = remember {
    try {
        loadResourceBytes("drawable/logo-black.png")
            ?.let { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }
    } catch (_: Exception) { null }
}

// ─── Convenience composables ─────────────────────────────────────────────────

/**
 * White logo – use on dark surfaces (sidebar, dark theme backgrounds).
 */
@Composable
fun LogoImage(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val bitmap = rememberLogoBitmap() ?: return
    Image(
        bitmap = bitmap,
        contentDescription = "StackPilot logo",
        modifier = modifier.size(size)
    )
}

/**
 * White logo at its natural aspect ratio.
 *
 * [LogoImage] pins both axes to [size], which squashes a non-square mark. This
 * variant takes the height and lets the width follow the PNG so the logo keeps
 * the proportions the designer drew.
 */
@Composable
fun BrandMark(height: Dp = 32.dp, modifier: Modifier = Modifier) {
    val bitmap = rememberLogoBitmap() ?: return
    Image(
        bitmap = bitmap,
        contentDescription = "StackPilot logo",
        modifier = modifier
            .height(height)
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
    )
}

/**
 * Black logo – use on light surfaces (startup screen, light theme backgrounds).
 */
@Composable
fun LogoImageBlack(size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val bitmap = rememberBlackLogoBitmap() ?: return
    Image(
        bitmap = bitmap,
        contentDescription = "StackPilot logo",
        modifier = modifier.size(size)
    )
}
