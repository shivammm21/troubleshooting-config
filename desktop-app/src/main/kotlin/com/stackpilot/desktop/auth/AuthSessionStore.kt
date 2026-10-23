package com.stackpilot.desktop.auth

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Persists the auth session to a local JSON file so the user stays
 * logged in across app restarts.
 *
 * Location: %APPDATA%\StackPilot\session.json  (Windows)
 *           ~/.stackpilot/session.json          (Linux / macOS)
 */
object AuthSessionStore {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val sessionFile: File by lazy {
        val base = System.getenv("APPDATA")
            ?: System.getProperty("user.home")
        val dir = Paths.get(base, ".stackpilot").toFile()
        dir.mkdirs()
        File(dir, "session.json")
    }

    /** Returns the saved session, or null if none exists / it's invalid. */
    fun load(): AuthSession? = try {
        if (!sessionFile.exists()) null
        else mapper.readValue(sessionFile, AuthSession::class.java)
    } catch (_: Exception) {
        null
    }

    /** Saves the session to disk. */
    fun save(session: AuthSession) {
        mapper.writerWithDefaultPrettyPrinter().writeValue(sessionFile, session)
    }

    /** Clears the saved session (logout). */
    fun clear() {
        sessionFile.delete()
    }
}
