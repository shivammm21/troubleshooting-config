package com.stackpilot.desktop.auth

/**
 * All auth-related UI states, data classes, and enums for the
 * StackPilot login / signup flow.
 */

/** Which screen the auth flow is currently showing. */
enum class AuthScreen {
    Login,          // Email + password, Sign in with Google, "Create account" link
    Signup,         // Name + email + password
    VerifyOtp,      // 6-digit OTP entry (after signup)
}

/** The result the auth flow returns once the user is authenticated. */
data class AuthResult(
    val token: String,
    val userId: String,
    val name: String,
    val email: String,
)

/** Persisted auth session stored locally so the user stays logged in. */
data class AuthSession(
    val token: String,
    val userId: String,
    val name: String,
    val email: String,
)
