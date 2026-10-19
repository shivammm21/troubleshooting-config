package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.project.model.ProjectModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RedisHostnameMismatchRule implements DiagnosticRule {

    static final Pattern REDIS_LOG = Pattern.compile(
            "RedisConnectionException|Unable to connect to Redis|Error connecting to Redis|UnknownHostException:\\s*redis",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String id() {
        return "redis-hostname-mismatch";
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        boolean composeHasRedis = context.composeHasService("redis");
        List<ProjectModels.HostBinding> hosts = context.project() == null
                ? List.of()
                : context.project().redisHosts();

        for (ProjectModels.HostBinding host : hosts) {
            if (isLoopback(host.value()) && composeHasRedis && context.hasNonInfraAppService()) {
                String serviceName = context.project().composeServices().stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).contains("redis"))
                        .findFirst()
                        .orElse("redis");
                findings.add(mismatch(
                        host,
                        serviceName,
                        logEvidence(context),
                        "Compose defines a Redis service, but the app is configured to use "
                                + host.value() + ". Inside Docker that hostname points at the app container, not Redis."
                ));
            }
        }

        if (findings.isEmpty() && REDIS_LOG.matcher(context.combinedLogs()).find()) {
            List<DiagnosticModels.Evidence> evidence = logEvidence(context);
            findings.add(new DiagnosticModels.Finding(
                    UUID.randomUUID().toString(),
                    id(),
                    "Redis connection failure in container logs",
                    "ERROR",
                    "Live logs show a Redis client failure. Compare spring.data.redis.host with the Compose service name.",
                    evidence,
                    null
            ));
        }
        return findings;
    }

    private DiagnosticModels.Finding mismatch(
            ProjectModels.HostBinding host,
            String serviceName,
            List<DiagnosticModels.Evidence> extra,
            String summary
    ) {
        List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
        evidence.add(new DiagnosticModels.Evidence("CONFIG", host.file() + " → " + host.key(), host.value()));
        evidence.addAll(extra);
                return new DiagnosticModels.Finding(
                UUID.randomUUID().toString(),
                id(),
                "Redis hostname does not match Compose service",
                "ERROR",
                summary,
                evidence,
                new DiagnosticModels.RepairSuggestion(
                        "Point the Redis client at the Compose service name `" + serviceName + "` instead of " + host.value() + ".",
                        List.of(new DiagnosticModels.FilePatch(host.file(), host.value(), serviceName))
                )
        );
    }

    private static boolean isLoopback(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("localhost") || v.equals("127.0.0.1") || v.startsWith("localhost:") || v.startsWith("127.0.0.1:");
    }

    private static List<DiagnosticModels.Evidence> logEvidence(DiagnosticContext context) {
        List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
        context.logsByContainer().forEach((container, logs) -> {
            if (REDIS_LOG.matcher(logs).find()) {
                evidence.add(new DiagnosticModels.Evidence("LOG", container, excerpt(logs, REDIS_LOG)));
            }
        });
        return evidence;
    }

    static String excerpt(String logs, Pattern pattern) {
        for (String line : logs.split("\\R")) {
            if (pattern.matcher(line).find()) {
                return line.length() > 400 ? line.substring(0, 400) : line;
            }
        }
        return logs.length() > 400 ? logs.substring(0, 400) : logs;
    }
}
