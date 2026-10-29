package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.project.model.ProjectModels;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature 4 – the README is treated as the specification, so a documented port or
 * variable that the runnable configuration does not honour becomes a finding that
 * cites the documentation line.
 */
class DocumentationConsistencyRuleTest {

    private final DocumentationConsistencyRule rule = new DocumentationConsistencyRule();

    @Test
    void reportsPortDriftBetweenReadmeAndCompose() {
        ProjectModels.Snapshot project = new ProjectModels.Snapshot(
                "p1",
                "/tmp/lab",
                List.of(),
                List.of("api"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProjectModels.ComposeService(
                        "api",
                        "app:1",
                        List.of(new ProjectModels.ServicePort(8081, 8080, "tcp")),
                        Map.of(),
                        List.of(),
                        List.of(),
                        false,
                        "docker-compose.yml")),
                List.of(new ProjectModels.DocFact(
                        "README.md", 12, "localhost-port", "8080",
                        "Open http://localhost:8080 to reach the API")),
                List.of()
        );
        DiagnosticContext context = new DiagnosticContext(
                project,
                new DockerModels.Status(true, "engine", 1, null),
                List.of(),
                Map.of()
        );

        List<DiagnosticModels.Finding> findings = rule.evaluate(context);

        assertFalse(findings.isEmpty());
        DiagnosticModels.Finding drift = findings.getFirst();
        assertEquals("documentation-mismatch", drift.ruleId());
        assertEquals("documentation", drift.category());
        assertTrue(drift.summary().contains("localhost:8080"), drift.summary());
        assertEquals("DOC", drift.evidence().getFirst().source());
        assertEquals("README.md:12", drift.evidence().getFirst().location());
    }

    @Test
    void reportsADocumentedVariableThatNothingDefines() {
        ProjectModels.Snapshot project = new ProjectModels.Snapshot(
                "p1",
                "/tmp/lab",
                List.of(),
                List.of("api"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProjectModels.ComposeService(
                        "api", "app:1", List.of(), Map.of(), List.of(), List.of(), false,
                        "docker-compose.yml")),
                List.of(new ProjectModels.DocFact(
                        "README.md", 20, "env-var", "REDIS_HOST",
                        "Set REDIS_HOST to the redis service name before starting the stack")),
                List.of()
        );
        DiagnosticContext context = new DiagnosticContext(
                project,
                new DockerModels.Status(true, "engine", 1, null),
                List.of(),
                Map.of()
        );

        List<DiagnosticModels.Finding> findings = rule.evaluate(context);

        assertTrue(findings.stream().anyMatch(f -> f.title().contains("REDIS_HOST")),
                findings.toString());
    }

    @Test
    void staysSilentWhenDocumentationMatchesTheRuntime() {
        ProjectModels.Snapshot project = new ProjectModels.Snapshot(
                "p1",
                "/tmp/lab",
                List.of(),
                List.of("api"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProjectModels.ComposeService(
                        "api",
                        "app:1",
                        List.of(new ProjectModels.ServicePort(8080, 8080, "tcp")),
                        Map.of("REDIS_HOST", "redis"),
                        List.of(),
                        List.of(),
                        false,
                        "docker-compose.yml")),
                List.of(new ProjectModels.DocFact(
                        "README.md", 12, "localhost-port", "8080",
                        "Open http://localhost:8080 to reach the API")),
                List.of()
        );
        DiagnosticContext context = new DiagnosticContext(
                project,
                new DockerModels.Status(true, "engine", 1, null),
                List.of(),
                Map.of()
        );

        assertTrue(rule.evaluate(context).isEmpty());
    }
}
