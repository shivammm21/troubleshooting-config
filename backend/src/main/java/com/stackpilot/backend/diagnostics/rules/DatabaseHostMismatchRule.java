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
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Generalises the Redis/Kafka hostname check to databases and brokers addressed
 * through a datasource URL or a bare host property.
 */
@Component
public class DatabaseHostMismatchRule implements DiagnosticRule {

    private static final Pattern DB_LOG = Pattern.compile(
            "connection refused|unable to connect|could not connect|connection to .* failed"
                    + "|Communications link failure|refused the connection|no route to host"
                    + "|UnknownHostException|name or service not known",
            Pattern.CASE_INSENSITIVE
    );

    private record Family(String key, String[] aliases) {
    }

    private static final List<Family> FAMILIES = List.of(
            new Family("postgres", new String[]{"postgres", "postgresql", "pgsql"}),
            new Family("mysql", new String[]{"mysql", "mariadb"}),
            new Family("mongo", new String[]{"mongo", "mongodb"}),
            new Family("rabbit", new String[]{"rabbit", "amqp"}),
            new Family("elastic", new String[]{"elastic", "opensearch"}),
            new Family("db", new String[]{"database", "db", "datastore"})
    );

    @Override
    public String id() {
        return "database-host-mismatch";
    }

    @Override
    public int order() {
        return 22;
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        if (context.project() == null || !context.hasNonInfraAppService()) {
            return findings;
        }

        List<DockerModels.ContainerSummary> connectionFailures = containersWithConnectionErrors(context);
        if (!connectionFailures.isEmpty()) {
            context.claim("CONNECTION");
        }

        for (ProjectModels.HostBinding binding : context.project().databaseHosts()) {
            String host = RuleSupport.hostOf(binding.value());
            if (!RuleSupport.isLoopback(host)) {
                continue;
            }
            String family = familyOf(binding.key());
            Optional<String> service = composeServiceFor(context, family);
            if (service.isEmpty()) {
                continue;
            }
            String serviceName = service.get();
            String replacement = binding.value().replace(host, serviceName);
            if (replacement.equals(binding.value())) {
                continue;
            }

            List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
            evidence.add(RuleSupport.config(binding));
            evidence.addAll(RedisHostnameMismatchRule.logEvidence(context, connectionFailures, DB_LOG));
            evidence.addAll(RedisHostnameMismatchRule.docEvidence(context, family));

            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Database connection points at loopback",
                    "ERROR",
                    "Compose provides `" + serviceName + "`, but " + binding.file() + " → "
                            + binding.key() + " uses " + host + ". A container cannot reach another "
                            + "container through loopback, so the connection is refused.",
                    evidence,
                    DiagnosticModels.RepairSuggestion.of(
                            "Replace the loopback host with the Compose service name `" + serviceName
                                    + "` and restart the app container.",
                            List.of(new DiagnosticModels.FilePatch(binding.file(), binding.value(), replacement)),
                            "LOW",
                            true),
                    92,
                    "configuration",
                    "db-host|" + binding.file() + "|" + binding.key(),
                    List.of(
                            "Apply the patch to " + binding.file(),
                            "Confirm the " + serviceName + " service is healthy",
                            "Restart the app container and re-run diagnostics")
            ));
        }
        return findings;
    }

    private static String familyOf(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        for (Family family : FAMILIES) {
            for (String alias : family.aliases()) {
                if (k.contains(alias)) {
                    return family.key();
                }
            }
        }
        return "db";
    }

    private static Optional<String> composeServiceFor(DiagnosticContext context, String family) {
        for (Family candidate : FAMILIES) {
            if (!candidate.key().equals(family)) {
                continue;
            }
            for (String alias : candidate.aliases()) {
                Optional<String> match = context.composeServiceContaining(alias);
                if (match.isPresent()) {
                    return match;
                }
            }
        }
        // Fall back to any non-infrastructure service when the family is generic.
        if ("db".equals(family)) {
            return context.composeServices().stream()
                    .filter(name -> !name.toLowerCase(Locale.ROOT).contains("redis"))
                    .filter(name -> !name.toLowerCase(Locale.ROOT).contains("kafka"))
                    .filter(name -> !name.toLowerCase(Locale.ROOT).contains("zookeeper"))
                    .findFirst();
        }
        return Optional.empty();
    }

    private static List<DockerModels.ContainerSummary> containersWithConnectionErrors(DiagnosticContext context) {
        List<DockerModels.ContainerSummary> failing = new ArrayList<>();
        for (DockerModels.ContainerSummary container : context.containers()) {
            String logs = context.logsByContainer().getOrDefault(container.id(), "");
            if (!logs.isBlank() && DB_LOG.matcher(logs).find()) {
                failing.add(container);
            }
        }
        return failing;
    }
}
