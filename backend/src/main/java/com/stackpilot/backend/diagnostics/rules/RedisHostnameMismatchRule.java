package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.project.model.ProjectModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects a Redis client pointed at loopback while Compose runs Redis as a
 * service, and enriches the finding with log and documentation evidence.
 */
@Component
public class RedisHostnameMismatchRule implements DiagnosticRule {

    static final Pattern REDIS_LOG = Pattern.compile(
            "RedisConnectionException|Unable to connect to Redis|Error connecting to Redis"
                    + "|UnknownHostException:\\s*redis|Redis.*connection refused",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String id() {
        return "redis-hostname-mismatch";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();

        List<DockerModels.ContainerSummary> failing = containersWithRedisErrors(context);
        List<DiagnosticModels.Evidence> orphanEvidence = orphanLogEvidence(context, REDIS_LOG);
        boolean anyLogSignal = !failing.isEmpty() || !orphanEvidence.isEmpty();
        if (anyLogSignal) {
            context.claim("CONNECTION");
        }

        boolean composeHasRedis = context.composeHasService("redis");
        List<ProjectModels.HostBinding> hosts = context.project() == null
                ? List.of()
                : context.project().redisHosts();

        for (ProjectModels.HostBinding host : hosts) {
            if (!RuleSupport.isLoopback(host.value()) || !composeHasRedis || !context.hasNonInfraAppService()) {
                continue;
            }
            String serviceName = context.composeServiceContaining("redis").orElse("redis");
            List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
            evidence.add(RuleSupport.config(host));
            evidence.addAll(logEvidence(context, failing, REDIS_LOG));
            evidence.addAll(docEvidence(context, "redis"));

            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Redis hostname does not match the Compose service",
                    "ERROR",
                    "Compose defines a Redis service named `" + serviceName + "`, but "
                            + host.file() + " configures " + host.key() + " = " + host.value()
                            + ". Inside the Compose network, a loopback address points at the app "
                            + "container itself, so every Redis command fails.",
                    evidence,
                    DiagnosticModels.RepairSuggestion.of(
                            "Point the Redis client at the Compose service name `" + serviceName
                                    + "` instead of " + host.value()
                                    + ", then restart the app container so it picks up the new configuration.",
                            List.of(new DiagnosticModels.FilePatch(host.file(), host.value(), serviceName)),
                            "LOW",
                            true),
                    95,
                    "configuration",
                    "redis-host|" + host.file() + "|" + host.key(),
                    List.of(
                            "Apply the patch to " + host.file(),
                            "Restart the app container (StackPilot can do this for you)",
                            "Re-run diagnostics and verify the Redis connection succeeds")
            ));
        }

        if (findings.isEmpty() && anyLogSignal) {
            List<DiagnosticModels.Evidence> evidence = new ArrayList<>(logEvidence(context, failing, REDIS_LOG));
            evidence.addAll(orphanEvidence);
            evidence.addAll(docEvidence(context, "redis"));
            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Redis connection failures in container logs",
                    "ERROR",
                    "Container logs show a Redis client failure. Compose declares "
                            + (composeHasRedis ? "a Redis service" : "no Redis service")
                            + ", so compare the configured host with the service name and the "
                            + "listener port Redis is actually bound to.",
                    evidence,
                    null,
                    60,
                    "runtime",
                    "redis-log|" + (evidence.isEmpty() ? "unknown" : evidence.getFirst().location()),
                    List.of(
                            "Confirm the Redis service is running: docker compose ps",
                            "Check spring.data.redis.host matches the Compose service name",
                            "Check the Redis port (default 6379) is exposed to the app network")
            ));
        }

        return findings;
    }

    /**
     * Log tails that are not attached to a listed container: the engine may know the
     * container while the summary list is empty (stopped container, permission gap),
     * and the log is still the strongest available evidence.
     */
    static List<DiagnosticModels.Evidence> orphanLogEvidence(DiagnosticContext context, Pattern pattern) {
        List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
        for (var entry : context.logsByContainer().entrySet()) {
            String logs = entry.getValue();
            if (logs == null || logs.isBlank() || !pattern.matcher(logs).find()) {
                continue;
            }
            if (context.containerByName(entry.getKey()).isPresent()) {
                continue;
            }
            boolean known = context.containers().stream()
                    .anyMatch(container -> container.id().equals(entry.getKey())
                            || container.name().equals(entry.getKey()));
            if (known) {
                continue;
            }
            evidence.add(RuleSupport.evidence("LOG", shortId(entry.getKey()), RuleSupport.excerpt(logs, pattern)));
            if (evidence.size() >= 3) {
                break;
            }
        }
        return evidence;
    }

    private static String shortId(String value) {
        if (value == null) {
            return "container";
        }
        return value.length() > 12 ? value.substring(0, 12) : value;
    }

    private static List<DockerModels.ContainerSummary> containersWithRedisErrors(DiagnosticContext context) {
        List<DockerModels.ContainerSummary> failing = new ArrayList<>();
        for (DockerModels.ContainerSummary container : context.containers()) {
            String logs = context.logsByContainer().getOrDefault(container.id(), "");
            if (!logs.isBlank() && REDIS_LOG.matcher(logs).find()) {
                failing.add(container);
            }
        }
        return failing;
    }

    static List<DiagnosticModels.Evidence> logEvidence(
            DiagnosticContext context,
            List<DockerModels.ContainerSummary> containers,
            Pattern pattern
    ) {
        List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
        for (DockerModels.ContainerSummary container : containers) {
            String logs = context.logsByContainer().getOrDefault(container.id(), "");
            if (logs.isBlank() || !pattern.matcher(logs).find()) {
                continue;
            }
            evidence.add(RuleSupport.evidence("LOG",
                    DiagnosticContext.label(container),
                    RuleSupport.excerpt(logs, pattern)));
        }
        return evidence;
    }

    /** Finds documentation that names the service, so the finding cites the intended setup. */
    static List<DiagnosticModels.Evidence> docEvidence(DiagnosticContext context, String serviceHint) {
        List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
        if (context.project() == null) {
            return evidence;
        }
        String needle = serviceHint.toLowerCase(Locale.ROOT);
        for (ProjectModels.DocFact fact : context.project().docFacts()) {
            String value = fact.value() == null ? "" : fact.value().toLowerCase(Locale.ROOT);
            String docContext = fact.context() == null ? "" : fact.context().toLowerCase(Locale.ROOT);
            if (!value.contains(needle) && !docContext.contains(needle)) {
                continue;
            }
            if (!"service-mention".equals(fact.kind())
                    && !"env-var".equals(fact.kind())
                    && !"localhost-port".equals(fact.kind())
                    && !"command".equals(fact.kind())) {
                continue;
            }
            evidence.add(RuleSupport.evidence("DOC",
                    fact.source() + ":" + fact.line(),
                    fact.context()));
            if (evidence.size() >= 2) {
                break;
            }
        }
        return evidence;
    }

    static String excerpt(String logs, Pattern pattern) {
        return RuleSupport.excerpt(logs, pattern);
    }
}
