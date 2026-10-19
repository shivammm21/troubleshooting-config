package com.stackpilot.desktop

/**
 * Top-level application state machine.
 *
 * The UI root observes this state and renders the appropriate screen.
 * State transitions are driven by [BackendLifecycle] events on a coroutine.
 */
sealed class AppState {

    /** Backend is starting; show the startup screen with the given message. */
    data class Starting(val message: String) : AppState()

    /**
     * Backend is ready; show the main dashboard.
     *
     * @param port    The local port the backend is listening on.
     * @param baseUrl The base URL, e.g. "http://127.0.0.1:49312".
     */
    data class Ready(val port: Int, val baseUrl: String, val sessionToken: String) : AppState()

    /**
     * A fatal error occurred during startup.
     * The startup screen switches to an error presentation.
     *
     * @param message Human-readable description of what went wrong.
     * @param cause   The underlying exception, if available.
     */
    data class Error(val message: String, val cause: Throwable? = null) : AppState()

    /** The application is shutting down; suppress further UI actions. */
    object ShuttingDown : AppState()
}
