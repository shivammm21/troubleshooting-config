package com.stackpilot.desktop.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Startup / loading screen shown while the Spring Boot backend is initialising.
 *
 * Displays a spinner, the application name, and the current startup status
 * message.  The screen is purely declarative – all state is passed in from
 * the application root.
 *
 * @param statusMessage Human-readable description of the current startup step.
 * @param isError       True if the startup has failed; switches the message to
 *                      an error colour and hides the spinner.
 */
@Composable
fun StartupScreen(
    statusMessage: String,
    isError: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "spinnerRotation"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Application title
            Text(
                text = "StackPilot",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Documentation-Aware DevOps Troubleshooting",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Spinner – only shown while loading, not on error.
            if (!isError) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .rotate(rotation),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Status message
            Text(
                text = statusMessage,
                fontSize = 13.sp,
                color = if (isError)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 400.dp)
            )
        }
    }
}
