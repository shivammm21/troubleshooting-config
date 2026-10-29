package com.stackpilot.desktop.auth

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

/**
 * Single source of truth for the StackPilot auth server.
 *
 * The Google OAuth redirect URI is derived from this base URL, so the value that
 * has to be registered in Google Cloud Console is always
 * `"${AuthConfig.baseUrl}/auth/callback"` — one fixed string, no per-run ports.
 *
 * Point it somewhere else with `STACKPILOT_AUTH_BASE_URL` (or the
 * `stackpilot.auth.baseUrl` system property), e.g. the deployed site.
 */
object AuthConfig {

    val baseUrl: String = (
        System.getenv("STACKPILOT_AUTH_BASE_URL")
            ?: System.getProperty("stackpilot.auth.baseUrl")
            ?: "http://localhost:3000"
        ).trimEnd('/')

    /** Exactly what Google Console needs under "Authorized redirect URIs". */
    val redirectUri: String = "$baseUrl/auth/callback"
}

/**
 * HTTP client for the StackPilot auth API hosted at the Next.js website
 * (default: http://localhost:3000).
 *
 * All calls are synchronous (called from Dispatchers.IO coroutines).
 */
class AuthClient(
    private val baseUrl: String = AuthConfig.baseUrl
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        // The JDK client defaults to HTTP/2 and sends an h2c "Upgrade" header on
        // plain-HTTP requests. Node's HTTP server treats that as a protocol
        // upgrade it has no listener for and drops the socket, so responses came
        // back as EOF and the app reported "Cannot reach …" even while the site
        // was happily serving curl. Pin HTTP/1.1, which is what Next.js speaks.
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // ── Login ──────────────────────────────────────────────────────────────

    fun login(email: String, password: String): AuthResult {
        val body = mapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val node = post("/api/auth/login", body)
        return AuthResult(
            token  = node.path("token").asText(),
            userId = node.path("user").path("id").asText(),
            name   = node.path("user").path("name").asText(),
            email  = node.path("user").path("email").asText(),
        )
    }

    // ── Sign-up (step 1 – sends OTP) ──────────────────────────────────────

    fun signup(name: String, email: String, password: String) {
        val body = mapper.writeValueAsString(mapOf("name" to name, "email" to email, "password" to password))
        post("/api/auth/signup", body)
        // On success the server returns { message: "Verification code sent..." }
        // No return value needed; caller proceeds to OTP screen.
    }

    // ── Verify OTP (step 2 – completes signup) ────────────────────────────

    fun verifyOtp(email: String, otp: String): AuthResult {
        val body = mapper.writeValueAsString(mapOf("email" to email, "otp" to otp))
        val node = post("/api/auth/verify-otp", body)
        return AuthResult(
            token  = node.path("token").asText(),
            userId = node.path("user").path("id").asText(),
            name   = node.path("user").path("name").asText(),
            email  = node.path("user").path("email").asText(),
        )
    }

    // ── Google Sign-In ─────────────────────────────────────────────────────

    fun googleSignIn(idToken: String): AuthResult {
        val body = mapper.writeValueAsString(mapOf("idToken" to idToken))
        val node = post("/api/auth/google", body)
        return AuthResult(
            token  = node.path("token").asText(),
            userId = node.path("user").path("id").asText(),
            name   = node.path("user").path("name").asText(),
            email  = node.path("user").path("email").asText(),
        )
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private fun post(path: String, json: String): com.fasterxml.jackson.databind.JsonNode {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl$path"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: HttpTimeoutException) {
            throw AuthException(
                "$baseUrl did not answer in time — check the terminal running the site."
            )
        } catch (_: IOException) {
            throw AuthException(
                "Cannot reach $baseUrl — start the site (npm run dev) and try again."
            )
        }
        val node = try {
            mapper.readTree(response.body())
        } catch (_: Exception) {
            throw AuthException(
                "Unexpected reply from $baseUrl (HTTP ${response.statusCode()})."
            )
        }

        if (response.statusCode() >= 400) {
            val msg = node.path("error").asText("Request failed (${response.statusCode()})")
            throw AuthException(msg)
        }
        return node
    }
}

/** Thrown when the web API returns an error (4xx / 5xx). */
class AuthException(message: String) : Exception(message)
