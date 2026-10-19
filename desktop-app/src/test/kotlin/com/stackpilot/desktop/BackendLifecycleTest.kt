package com.stackpilot.desktop

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Integration tests for [BackendLifecycle].
 *
 * These tests start a real Spring Boot context, verify the backend comes up,
 * and confirm graceful shutdown.  They require no Docker or external services.
 *
 * Each test gets a fresh [BackendLifecycle] instance; the [afterEach] teardown
 * ensures the context is stopped even if a test fails mid-way.
 */
@Timeout(60, unit = TimeUnit.SECONDS)
class BackendLifecycleTest {

    private val lifecycle = BackendLifecycle()

    @AfterEach
    fun tearDown() = kotlinx.coroutines.runBlocking {
        lifecycle.stop()
    }

    @Test
    fun `start assigns a non-zero port`() = runTest {
        val port = lifecycle.start()
        assertTrue(port > 0, "Expected port > 0 but got $port")
        assertEquals(port, lifecycle.port, "port property should match returned value")
    }

    @Test
    fun `baseUrl reflects the assigned port`() = runTest {
        val port = lifecycle.start()
        assertEquals("http://127.0.0.1:$port", lifecycle.baseUrl)
    }

    @Test
    fun `waitUntilReady returns true after start`() = runTest {
        lifecycle.start()
        val ready = lifecycle.waitUntilReady(timeoutMs = 20_000)
        assertTrue(ready, "Expected backend to become ready within 20 seconds")
        assertTrue(lifecycle.sessionToken.isNotBlank(), "Session token should be issued after start")
    }

    @Test
    fun `isRunning is true after start and false after stop`() = runTest {
        assertFalse(lifecycle.isRunning, "Should not be running before start()")

        lifecycle.start()
        lifecycle.waitUntilReady(timeoutMs = 20_000)
        assertTrue(lifecycle.isRunning, "Should be running after start()")

        lifecycle.stop()
        assertFalse(lifecycle.isRunning, "Should not be running after stop()")
    }

    @Test
    fun `stop is idempotent – calling it twice does not throw`() = runTest {
        lifecycle.start()
        lifecycle.waitUntilReady(timeoutMs = 20_000)

        lifecycle.stop() // first call
        lifecycle.stop() // second call – must not throw
    }

    @Test
    fun `stop without start is safe`() = runTest {
        // Should complete without exception.
        lifecycle.stop()
    }
}
