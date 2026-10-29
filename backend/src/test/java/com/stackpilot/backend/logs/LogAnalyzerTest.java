package com.stackpilot.backend.logs;

import com.stackpilot.backend.logs.model.LogModels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogAnalyzerTest {

    private final LogAnalyzer analyzer = new LogAnalyzer();

    @Test
    void groupsRepeatedFailuresIntoOneSignature() {
        String raw = """
                2026-10-29T10:00:01.123456789Z ERROR 1 --- [main] c.s.App : java.net.ConnectException: Connection refused
                2026-10-29T10:00:02.123456789Z ERROR 1 --- [main] c.s.App : java.net.ConnectException: Connection refused
                2026-10-29T10:00:03.123456789Z ERROR 1 --- [main] c.s.App : java.net.ConnectException: Connection refused
                2026-10-29T10:00:04.123456789Z  INFO 1 --- [main] c.s.App : Started App in 3.2 seconds
                """;

        LogModels.Summary summary = analyzer.analyze(raw);

        assertEquals(3, summary.errorLines());
        assertTrue(summary.hasErrors());
        assertEquals(1, summary.signatures().size());
        LogModels.Signature signature = summary.signatures().getFirst();
        assertEquals("CONNECTION", signature.category());
        assertEquals("ERROR", signature.severity());
        assertEquals(3, signature.count());
        // The Docker timestamp prefix is stripped before classification.
        assertFalse(signature.sample().startsWith("2026-"));
    }

    @Test
    void classifiesDistinctFailureFamilies() {
        String raw = """
                ERROR c.z.RedisHealthCheck : Unable to connect to Redis at localhost:6379
                ERROR c.z.Db : java.net.UnknownHostException: postgres
                ERROR o.s.b.w.e.t.TomcatWebServer : Port 8080 was already in use
                ERROR o.s.b.d.LoggingFailureAnalysisReporter : Application failed to start
                """;

        LogModels.Summary summary = analyzer.analyze(raw);
        List<String> categories = summary.categories();

        assertTrue(categories.contains("CONNECTION"), categories.toString());
        assertTrue(categories.contains("DNS"), categories.toString());
        assertTrue(categories.contains("PORT"), categories.toString());
        assertTrue(categories.contains("CRASH"), categories.toString());
    }

    @Test
    void emptyLogProducesEmptySummary() {
        LogModels.Summary summary = analyzer.analyze("");

        assertEquals(0, summary.totalLines());
        assertFalse(summary.hasErrors());
        assertTrue(summary.signatures().isEmpty());
    }
}
