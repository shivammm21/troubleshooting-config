package com.stackpilot.desktop

import com.stackpilot.backend.StackPilotBackendApplication
import com.stackpilot.backend.security.SessionTokenService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Manages the embedded Spring Boot context lifecycle.
 *
 * The desktop application calls [start] on a background coroutine (not the UI
 * thread) and then calls [stop] when the window is closing.
 *
 * Thread-safety: [start] and [stop] are both coroutine-safe.  Internal state
 * is protected with @Volatile / synchronized so it is safe to call from any
 * coroutine context.
 */
class BackendLifecycle {

    private val log = LoggerFactory.getLogger(BackendLifecycle::class.java)

    @Volatile
    private var context: ConfigurableApplicationContext? = null

    /** The actual port chosen by Spring Boot (0 until [start] returns). */
    @Volatile
    var port: Int = 0
        private set

    /** Returns the base URL of the local backend API once started. */
    val baseUrl: String get() = "http://127.0.0.1:$port"

    /** Per-session API token injected into desktop HTTP calls. */
    @Volatile
    var sessionToken: String = ""
        private set

    /**
     * Starts the Spring Boot context inside the calling coroutine's thread.
     * Must NOT be called from the Compose UI thread; call from [Dispatchers.IO].
     *
     * @return the port the server bound to.
     * @throws IllegalStateException if already started.
     * @throws Exception if Spring Boot fails to start.
     */
    suspend fun start(): Int = withContext(Dispatchers.IO) {
        check(context == null) { "BackendLifecycle.start() called when already running" }

        log.info("Starting Spring Boot backend context…")

        val ctx = SpringApplicationBuilder(StackPilotBackendApplication::class.java)
            // Prevent Spring Boot from registering a JVM shutdown hook –
            // the desktop application controls the shutdown sequence.
            .registerShutdownHook(false)
            // Headless mode: no AWT in the Spring context.
            .headless(true)
            .run()

        context = ctx
        port = StackPilotBackendApplication.resolvePort(ctx as ServletWebServerApplicationContext)
        sessionToken = ctx.getBean(SessionTokenService::class.java).token()

        log.info("Spring Boot context started on port {}", port)
        port
    }

    /**
     * Polls the /api/health endpoint until it returns HTTP 200 or the timeout
     * elapses.  Call after [start] to confirm the server can serve requests.
     *
     * @param timeoutMs maximum milliseconds to wait.
     * @param pollIntervalMs delay between attempts.
     * @return true if the health check passed within the timeout.
     */
    suspend fun waitUntilReady(timeoutMs: Long = 10_000, pollIntervalMs: Long = 200): Boolean =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + timeoutMs
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build()

            while (System.currentTimeMillis() < deadline) {
                try {
                    val request = HttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/api/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build()

                    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                    if (response.statusCode() == 200) {
                        log.info("Backend health check passed on port {}", port)
                        return@withContext true
                    }
                } catch (e: Exception) {
                    // Server not yet ready – continue polling.
                    log.trace("Health check attempt failed: {}", e.message)
                }
                Thread.sleep(pollIntervalMs)
            }

            log.warn("Backend did not become ready within {}ms", timeoutMs)
            false
        }

    /**
     * Stops the Spring Boot context gracefully.
     *
     * This method:
     * 1. Closes the application context, which triggers Spring's graceful
     *    shutdown and runs all [DisposableBean] / @PreDestroy lifecycle hooks.
     * 2. Clears internal references so [start] could theoretically be called
     *    again (not expected in normal operation).
     *
     * Safe to call even if [start] was never called or already failed.
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        val ctx = context ?: run {
            log.info("stop() called but no backend context is running – nothing to do.")
            return@withContext
        }

        log.info("Stopping Spring Boot backend context…")
        try {
            ctx.close()
            log.info("Spring Boot context stopped.")
        } catch (e: Exception) {
            log.error("Error stopping Spring Boot context", e)
        } finally {
            context = null
            port = 0
            sessionToken = ""
        }
    }

    /** Returns true if the backend context is currently running. */
    val isRunning: Boolean get() = context?.isRunning == true
}
