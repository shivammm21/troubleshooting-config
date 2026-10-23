package com.stackpilot.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.stackpilot.desktop.auth.AuthResult
import com.stackpilot.desktop.auth.AuthSession
import com.stackpilot.desktop.auth.AuthSessionStore
import com.stackpilot.desktop.components.loadLogoBytes
import com.stackpilot.desktop.screens.AuthScreen
import com.stackpilot.desktop.screens.DashboardScreen
import com.stackpilot.desktop.screens.StartupScreen
import com.stackpilot.desktop.theme.StackPilotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.stackpilot.desktop.Main")

/**
 * JVM entry point for StackPilot.
 *
 * Startup flow:
 * 1. Check disk for a saved auth session.
 *    a. Found   → skip login, start backend directly.
 *    b. Missing → show auth screen (login / signup / OTP).
 * 2. Once authenticated, start Spring Boot backend.
 * 3. Poll /api/health until ready.
 * 4. Show the main dashboard.
 *
 * Shutdown:
 * - Transition to ShuttingDown state.
 * - Stop Spring Boot context.
 * - Exit JVM.
 */
fun main() = application {
    val backend = remember { BackendLifecycle() }
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    var appState: AppState by remember { mutableStateOf(AppState.CheckingSession) }

    // Cached auth info (set after login, used to greet user in dashboard).
    var authSession by remember { mutableStateOf<AuthSession?>(null) }

    // ── Session check (runs once on launch) ──────────────────────────────
    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) { AuthSessionStore.load() }
        if (saved != null) {
            authSession = saved
            log.info("Restored session for {}", saved.email)
            startBackend(backend, appScope, saved.name, saved.email) { appState = it }
        } else {
            appState = AppState.NeedsAuth
        }
    }

    val windowState = rememberWindowState(width = 1380.dp, height = 880.dp)

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
                log.info("Backend stopped. Exiting.")
                exitApplication()
            }
        },
        state = windowState,
        title = "StackPilot",
        icon = windowIcon
    ) {
        when (val state = appState) {

            // ── Instant session check (no UI flash) ──────────────────────
            is AppState.CheckingSession -> {
                // Render nothing while we check disk — too fast to see.
            }

            // ── Auth screens (light theme — matches the login design) ────
            is AppState.NeedsAuth -> {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    AuthScreen(
                        onAuthenticated = { result: AuthResult ->
                            val session = AuthSession(
                                token  = result.token,
                                userId = result.userId,
                                name   = result.name,
                                email  = result.email,
                            )
                            authSession = session
                            // Persist so next launch skips login.
                            appScope.launch(Dispatchers.IO) { AuthSessionStore.save(session) }
                            // Start backend.
                            startBackend(backend, appScope, result.name, result.email) { appState = it }
                        }
                    )
                }
            }

            // ── Backend startup ───────────────────────────────────────────
            is AppState.Starting -> StackPilotTheme {
                StartupScreen(statusMessage = state.message, isError = false)
            }

            is AppState.Error -> StackPilotTheme {
                StartupScreen(statusMessage = state.message, isError = true)
            }

            // ── Main dashboard ────────────────────────────────────────────
            is AppState.Ready -> StackPilotTheme {
                DashboardScreen(
                    backendPort  = state.port,
                    backendUrl   = state.baseUrl,
                    sessionToken = state.sessionToken,
                )
            }

            is AppState.ShuttingDown -> StackPilotTheme {
                StartupScreen(statusMessage = "Shutting down…", isError = false)
            }
        }
    }
}

/** Starts Spring Boot on a background coroutine and updates [setState]. */
private fun startBackend(
    backend: BackendLifecycle,
    scope: CoroutineScope,
    userName: String,
    userEmail: String,
    setState: (AppState) -> Unit,
) {
    scope.launch(Dispatchers.IO) {
        try {
            setState(AppState.Starting("Starting Spring Boot context…"))
            backend.start()

            setState(AppState.Starting("Waiting for backend readiness…"))
            val ready = backend.waitUntilReady(timeoutMs = 30_000)

            if (ready) {
                setState(
                    AppState.Ready(
                        port         = backend.port,
                        baseUrl      = backend.baseUrl,
                        sessionToken = backend.sessionToken,
                        userName     = userName,
                        userEmail    = userEmail,
                    )
                )
                log.info("Backend ready at {} for user {}", backend.baseUrl, userEmail)
            } else {
                setState(AppState.Error("Backend did not become ready within 30 seconds."))
            }
        } catch (e: Exception) {
            log.error("Backend startup failed", e)
            setState(AppState.Error("Backend startup failed: ${e.message ?: e.javaClass.simpleName}", e))
        }
    }
}
