package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.diagnostics.rules.RedisHostnameMismatchRule;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.project.model.ProjectModels;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisHostnameMismatchRuleTest {

    private final RedisHostnameMismatchRule rule = new RedisHostnameMismatchRule();

    @Test
    void flagsLocalhostWhenComposeDefinesRedisAndApp() {
        ProjectModels.Snapshot project = new ProjectModels.Snapshot(
                "p1",
                "/tmp/lab",
                List.of(),
                List.of("redis", "api"),
                List.of(new ProjectModels.HostBinding("application.yml", "spring.data.redis.host", "localhost")),
                List.of()
        );
        DiagnosticContext context = new DiagnosticContext(
                project,
                new DockerModels.Status(true, "engine", 2, null),
                List.of(),
                Map.of()
        );

        List<DiagnosticModels.Finding> findings = rule.evaluate(context);
        assertEquals(1, findings.size());
        assertEquals("redis-hostname-mismatch", findings.getFirst().ruleId());
        assertTrue(findings.getFirst().repair().patches().getFirst().replacementSnippet().equals("redis"));
    }

    @Test
    void usesLogEvidenceWhenConfigIsMissing() {
        DiagnosticContext context = new DiagnosticContext(
                null,
                new DockerModels.Status(true, "engine", 1, null),
                List.of(),
                Map.of("abc", "Caused by: RedisConnectionException: Unable to connect to Redis")
        );

        List<DiagnosticModels.Finding> findings = rule.evaluate(context);
        assertFalse(findings.isEmpty());
        assertEquals("LOG", findings.getFirst().evidence().getFirst().source());
    }
}
