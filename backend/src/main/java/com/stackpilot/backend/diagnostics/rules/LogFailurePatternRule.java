package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.logs.model.LogModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Feature 2 – Log Analysis and Error Detection (reporting half).
 *
 * <p>Parses every container's log tail into grouped failure signatures and turns
 * the ones no specific rule already explained into findings. Runs last so the
 * Redis/Kafka/database/port rules get the first word.
 */
@Component
public class LogFailurePatternRule implements DiagnosticRule {

    private static final int MAX_FINDINGS = 12;
    private static final int MAX_PER_CONTAINER = 2;
    private static final int MAX_SAMPLES = 3;

    @Override
    public String id() {
        return "log-failure-pattern";
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public String category() {
        return "runtime";
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();

        for (DockerModels.ContainerSummary container : context.containers()) {
            if (findings.size() >= MAX_FINDINGS) {
                break;
            }
            LogModels.Summary summary = context.summaryFor(container);
            if (summary.signatures().isEmpty()) {
                continue;
            }
            boolean containerExplained = context.claimed("container:" + container.name())
                    && container.stopped();

            Map<String, List<LogModels.Signature>> byCategory = new LinkedHashMap<>();
            for (LogModels.Signature signature : summary.signatures()) {
                if (!"ERROR".equals(signature.severity())) {
                    continue;
                }
                String category = signature.category() == null ? "OTHER" : signature.category();
                if ("OTHER".equals(category)) {
                    continue;
                }
                if (context.claimed(category)) {
                    continue;
                }
                byCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(signature);
            }

            int produced = 0;
            for (Map.Entry<String, List<LogModels.Signature>> entry : byCategory.entrySet()) {
                if (produced >= MAX_PER_CONTAINER || findings.size() >= MAX_FINDINGS) {
                    break;
                }
                if (containerExplained) {
                    break;
                }
                String category = entry.getKey();
                List<LogModels.Signature> signatures = entry.getValue();

                List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
                int total = 0;
                for (LogModels.Signature signature : signatures) {
                    total += signature.count();
                    if (evidence.size() >= MAX_SAMPLES) {
                        continue;
                    }
                    evidence.add(RuleSupport.evidence("LOG",
                            DiagnosticContext.label(container) + " line " + signature.line(),
                            signature.sample()));
                }

                findings.add(new DiagnosticModels.Finding(
                        RuleSupport.id(),
                        id(),
                        describe(category) + " errors in `" + container.name() + "`",
                        "ERROR",
                        total + " log line(s) in " + DiagnosticContext.label(container)
                                + " match the " + category + " failure class. Grouped signature: "
                                + RuleSupport.truncate(signatures.getFirst().signature(), 180),
                        evidence,
                        null,
                        confidenceFor(category),
                        "runtime",
                        "log|" + container.name() + "|" + category,
                        nextSteps(category)
                ));
                produced++;
            }
        }

        return findings;
    }

    private static String describe(String category) {
        return switch (category) {
            case "CONNECTION" -> "Connection";
            case "DNS" -> "DNS resolution";
            case "AUTH" -> "Authentication";
            case "PORT" -> "Port binding";
            case "OOM" -> "Out-of-memory";
            case "CONFIG" -> "Configuration";
            case "MIGRATION" -> "Database migration";
            case "TIMEOUT" -> "Timeout";
            case "CRASH" -> "Startup crash";
            case "DEPENDENCY" -> "Dependency";
            default -> "Unclassified";
        };
    }

    private static int confidenceFor(String category) {
        return switch (category) {
            case "CONNECTION", "DNS", "AUTH", "OOM", "PORT", "CRASH" -> 75;
            case "CONFIG", "MIGRATION", "TIMEOUT" -> 70;
            default -> 55;
        };
    }

    private static List<String> nextSteps(String category) {
        return switch (category) {
            case "CONNECTION" -> List.of(
                    "Confirm the target service is running and reachable from the container network",
                    "Check the configured host/port against the Compose service name");
            case "DNS" -> List.of(
                    "Verify the hostname matches a Compose service name exactly",
                    "Check the container's DNS resolution: docker exec <container> getent hosts <name>");
            case "AUTH" -> List.of(
                    "Check the username/password (or token) supplied to the service",
                    "Confirm the credentials in .env match the server configuration");
            case "PORT" -> List.of(
                    "Find the process holding the port and stop it",
                    "Or change the configured port and restart the container");
            case "OOM" -> List.of(
                    "Raise the container memory limit",
                    "Look for unbounded caches or batch sizes in the workload");
            case "CONFIG" -> List.of(
                    "Read the failing property in the stack trace",
                    "Confirm the value is present in .env or the service environment");
            case "MIGRATION" -> List.of(
                    "Check the migration files and the schema they expect",
                    "Confirm the database user may alter the schema");
            case "TIMEOUT" -> List.of(
                    "Check the dependency's latency and connection pool size",
                    "Increase the client timeout only after confirming the server is healthy");
            case "CRASH" -> List.of(
                    "Read the full stack trace: docker logs --tail 300 <container>",
                    "Fix the reported cause and restart the container");
            default -> List.of(
                    "Inspect the full container log around the first occurrence",
                    "Re-run diagnostics after changing configuration");
        };
    }
}
