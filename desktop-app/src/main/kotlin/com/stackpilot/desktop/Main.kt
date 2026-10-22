package com.stackpilot.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.stackpilot.desktop.components.loadLogoBytes
import com.stackpilot.desktop.screens.DashboardScreen
import com.stackpilot.desktop.screens.StartupScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.stackpilot.desktop.Main")

/**
 * JVM entry point for StackPilot.
 *
 * Lifecycle overview:
 * 1. A [BackendLifecycle] instance is created.
 * 2. The Compose window opens immediately showing a startup screen.
 * 3. A background coroutine starts Spring Boot ([BackendLifecycle.start]).
 * 4. Once started, [BackendLifecycle.waitUntilReady] polls /api/health.
 * 5. On success the state transitions to [AppState.Ready] and the dashboard
 *    is shown.
 * 6. When the window close button is pressed, [BackendLifecycle.stop] is
 *    called on a background coroutine before the JVM exits.
 */
fun main() = application {
    val backend = remember { BackendLifecycle() }

    // Application-scoped coroutine scope – cancelled on window close.
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Mutable UI state – drives which screen is rendered.
    var appState: AppState by remember { mutableStateOf(AppState.Starting("Initialising…")) }

    // Start Spring Boot once, asynchronously from the UI thread.
    LaunchedEffect(Unit) {
        log.info("Launching backend startup coroutine")
        appScope.launch(Dispatchers.IO) {
            try {
                appState = AppState.Starting("Starting Spring Boot context…")
                backend.start()

                appState = AppState.Starting("Waiting for backend readiness…")
                val ready = backend.waitUntilReady(timeoutMs = 30_000)

                if (ready) {
                    appState = AppState.Ready(
                        port = backend.port,
                        baseUrl = backend.baseUrl,
                        sessionToken = backend.sessionToken
                    )
                    log.info("Backend ready at {}", backend.baseUrl)
                } else {
                    appState = AppState.Error(
                        "Backend did not become ready within 30 seconds. " +
                                "Check the application logs for details."
                    )
                }
            } catch (e: Exception) {
                log.error("Backend startup failed", e)
                appState = AppState.Error(
                    "Backend startup failed: ${e.message ?: e.javaClass.simpleName}",
                    cause = e
                )
            }
        }
    }

    val windowState = rememberWindowState(width = 1380.dp, height = 880.dp)

    // Load the window title-bar icon from the bundled resource.
    // BitmapPainter wraps the decoded bitmap so Window.icon accepts it.
    val windowIcon = remember {
        try {
            loadLogoBytes()
                ?.let { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }
                ?.let { BitmapPainter(it) }
        } catch (_: Exception) { null }
    }

    Window(
        onCloseRequest = {
            appState = AppState.ShuttingDown
            appScope.launch(Dispatchers.IO) {
                log.info("Window closing – stopping backend…")
                backend.stop()
                log.info("Backend stopped. Exiting application.")
                exitApplication()
            }
        },
        state = windowState,
        title = "StackPilot",
        icon = windowIcon
    ) {
        com.stackpilot.desktop.theme.StackPilotTheme {
            when (val state = appState) {
                is AppState.Starting -> StartupScreen(
                    statusMessage = state.message,
                    isError = false
                )

                is AppState.Error -> StartupScreen(
                    statusMessage = state.message,
                    isError = true
                )

                is AppState.Ready -> DashboardScreen(
                    backendPort = state.port,
                    backendUrl = state.baseUrl,
                    sessionToken = state.sessionToken
                )

                is AppState.ShuttingDown -> StartupScreen(
                    statusMessage = "Shutting down…",
                    isError = false
                )
            }
        }
    }
}
