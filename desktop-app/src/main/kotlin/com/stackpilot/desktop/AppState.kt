package com.stackpilot.desktop

/**
 * Top-level application state machine.
 *
 * Flow:
 *   CheckingSession → (session found) → Starting → Ready
 *   CheckingSession → (no session)    → NeedsAuth
 *   NeedsAuth       → (authenticated) → Starting → Ready
 */
sealed class AppState {

    /** Checking local disk for a saved auth session (instant). */
    object CheckingSession : AppState()

    /** No valid session — show the login / signup screen. */
    object NeedsAuth : AppState()

    /** Authenticated; backend is starting. */
    data class Starting(val message: String) : AppState()

    /**
     * Backend is ready; show the main dashboard.
     * @param port       Local port the backend is listening on.
     * @param baseUrl    Full base URL, e.g. "http://127.0.0.1:49312".
     * @param userName   Display name of the authenticated user.
     * @param userEmail  Email of the authenticated user.
     */
    data class Ready(
        val port: Int,
        val baseUrl: String,
        val sessionToken: String,
        val userName: String,
        val userEmail: String,
    ) : AppState()

    /**
     * Fatal startup error.
     * @param message Human-readable description.
     * @param cause   Underlying exception if available.
     */
    data class Error(val message: String, val cause: Throwable? = null) : AppState()

    /** Shutting down — suppress further UI actions. */
    object ShuttingDown : AppState()
}
