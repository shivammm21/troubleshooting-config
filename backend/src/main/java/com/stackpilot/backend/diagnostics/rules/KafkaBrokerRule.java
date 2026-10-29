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
 * Detects Kafka bootstrap servers that point at loopback while Compose runs the
 * broker as a service, and rewrites them to the internal listener.
 */
@Component
public class KafkaBrokerRule implements DiagnosticRule {

    static final Pattern KAFKA_LOG = Pattern.compile(
            "Bootstrap broker|Connection to node -1|TimeoutException: Topic"
                    + "|Failed to update metadata|Fatal error during Kafka|disconnect from node",
            Pattern.CASE_INSENSITIVE
    );

    /** Default port the broker advertises on the internal Compose listener. */
    private static final String INTERNAL_PORT = "9092";

    @Override
    public String id() {
        return "kafka-bootstrap-mismatch";
    }

    @Override
    public int order() {
        return 21;
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();

        List<DockerModels.ContainerSummary> failing = new ArrayList<>();
        for (DockerModels.ContainerSummary container : context.containers()) {
            String logs = context.logsByContainer().getOrDefault(container.id(), "");
            if (!logs.isBlank() && KAFKA_LOG.matcher(logs).find()) {
                failing.add(container);
            }
        }
        if (!failing.isEmpty()) {
            context.claim("CONNECTION");
        }

        boolean composeHasKafka = context.composeHasService("kafka");
        List<ProjectModels.HostBinding> bootstraps = context.project() == null
                ? List.of()
                : context.project().kafkaBootstraps();

        for (ProjectModels.HostBinding binding : bootstraps) {
            String host = RuleSupport.hostOf(binding.value());
            if (!RuleSupport.isLoopback(host) || !composeHasKafka || !context.hasNonInfraAppService()) {
                continue;
            }
            String service = context.composeServices().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).contains("kafka")
                            && !name.toLowerCase(Locale.ROOT).contains("zookeeper"))
                    .findFirst()
                    .orElse("kafka");

            String replacement = rewriteBootstrap(binding.value(), service);

            List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
            evidence.add(RuleSupport.config(binding));
            evidence.addAll(RedisHostnameMismatchRule.logEvidence(context, failing, KAFKA_LOG));
            evidence.addAll(RedisHostnameMismatchRule.docEvidence(context, "kafka"));

            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Kafka bootstrap servers point at loopback",
                    "ERROR",
                    "Compose runs the broker as `" + service + "`, but the app bootstraps "
                            + binding.value() + ". Other containers resolve loopback to themselves, "
                            + "so metadata requests never reach the broker.",
                    evidence,
                    DiagnosticModels.RepairSuggestion.of(
                            "Use the Kafka Compose service hostname with the internal listener port "
                                    + INTERNAL_PORT + ".",
                            List.of(new DiagnosticModels.FilePatch(binding.file(), binding.value(), replacement)),
                            "LOW",
                            true),
                    95,
                    "configuration",
                    "kafka-bootstrap|" + binding.file() + "|" + binding.key(),
                    List.of(
                            "Apply the patch to " + binding.file(),
                            "Ensure the broker advertises an internal listener (KAFKA_ADVERTISED_LISTENERS)",
                            "Restart the app container and re-run diagnostics")
            ));
        }

        if (findings.isEmpty() && !failing.isEmpty()) {
            List<DiagnosticModels.Evidence> evidence =
                    new ArrayList<>(RedisHostnameMismatchRule.logEvidence(context, failing, KAFKA_LOG));
            evidence.addAll(RedisHostnameMismatchRule.docEvidence(context, "kafka"));
            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Kafka client cannot reach the broker",
                    "ERROR",
                    "Logs show a Kafka bootstrap or metadata failure. Compare "
                            + "spring.kafka.bootstrap-servers with the Compose service name and the "
                            + "advertised listener configured on the broker.",
                    evidence,
                    null,
                    60,
                    "runtime",
                    "kafka-log|" + (evidence.isEmpty() ? "unknown" : evidence.getFirst().location()),
                    List.of(
                            "Confirm the broker and Zookeeper are running: docker compose ps",
                            "Check the app's bootstrap server matches the service name and internal port",
                            "Check ADVERTISED_LISTENERS in the broker environment")
            ));
        }

        return findings;
    }

    /** localhost:9092 -> kafka:9092 (and keeps any additional brokers intact). */
    static String rewriteBootstrap(String value, String service) {
        if (value == null) {
            return service + ":" + INTERNAL_PORT;
        }
        String rewritten = value
                .replace("localhost", service)
                .replace("127.0.0.1", service)
                .replace("0.0.0.0", service);
        if (!rewritten.contains(":")) {
            rewritten = rewritten + ":" + INTERNAL_PORT;
        }
        return rewritten;
    }
}
