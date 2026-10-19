package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.rules.KafkaBrokerRule;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.project.model.ProjectModels;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaBrokerRuleTest {

    private final KafkaBrokerRule rule = new KafkaBrokerRule();

    @Test
    void flagsLocalhostBootstrapAgainstComposeKafka() {
        ProjectModels.Snapshot project = new ProjectModels.Snapshot(
                "p1",
                "/tmp/lab",
                List.of(),
                List.of("kafka", "api"),
                List.of(),
                List.of(new ProjectModels.HostBinding("application.yml", "spring.kafka.bootstrap-servers", "localhost:9092"))
        );
        var findings = rule.evaluate(new DiagnosticContext(
                project,
                new DockerModels.Status(true, "engine", 2, null),
                List.of(),
                Map.of()
        ));
        assertEquals(1, findings.size());
        assertTrue(findings.getFirst().repair().patches().getFirst().replacementSnippet().contains("kafka"));
    }
}
