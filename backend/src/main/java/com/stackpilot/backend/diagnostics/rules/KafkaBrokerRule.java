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
public class KafkaBrokerRule implements DiagnosticRule {

    static final Pattern KAFKA_LOG = Pattern.compile(
            "Bootstrap broker|Connection to node -1|TimeoutException: Topic|Failed to update metadata",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String id() {
        return "kafka-bootstrap-mismatch";
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        boolean composeHasKafka = context.composeHasService("kafka");
        List<ProjectModels.HostBinding> bootstraps = context.project() == null
                ? List.of()
                : context.project().kafkaBootstraps();

        for (ProjectModels.HostBinding binding : bootstraps) {
            String value = binding.value().toLowerCase(Locale.ROOT);
            boolean loopback = value.contains("localhost") || value.contains("127.0.0.1");
            if (loopback && composeHasKafka && context.hasNonInfraAppService()) {
                String service = context.project().composeServices().stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).contains("kafka")
                                && !name.toLowerCase(Locale.ROOT).contains("zookeeper"))
                        .findFirst()
                        .orElse("kafka");
                String replacementHost = binding.value().replace("localhost", service).replace("127.0.0.1", service);
                List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
                evidence.add(new DiagnosticModels.Evidence("CONFIG", binding.file() + " → " + binding.key(), binding.value()));
                context.logsByContainer().forEach((container, logs) -> {
                    if (KAFKA_LOG.matcher(logs).find()) {
                        evidence.add(new DiagnosticModels.Evidence("LOG", container, RedisHostnameMismatchRule.excerpt(logs, KAFKA_LOG)));
                    }
                });
                findings.add(new DiagnosticModels.Finding(
                        UUID.randomUUID().toString(),
                        id(),
                        "Kafka bootstrap servers point at localhost",
                        "ERROR",
                        "Compose runs Kafka as `" + service + "`, but the app bootstraps " + binding.value()
                                + ". From another container that address is wrong.",
                        evidence,
                        new DiagnosticModels.RepairSuggestion(
                                "Use the Kafka Compose service hostname and internal listener port.",
                                List.of(new DiagnosticModels.FilePatch(binding.file(), binding.value(), replacementHost))
                        )
                ));
            }
        }

        if (findings.isEmpty() && KAFKA_LOG.matcher(context.combinedLogs()).find()) {
            List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
            context.logsByContainer().forEach((container, logs) -> {
                if (KAFKA_LOG.matcher(logs).find()) {
                    evidence.add(new DiagnosticModels.Evidence("LOG", container, RedisHostnameMismatchRule.excerpt(logs, KAFKA_LOG)));
                }
            });
            findings.add(new DiagnosticModels.Finding(
                    UUID.randomUUID().toString(),
                    id(),
                    "Kafka client cannot reach the broker",
                    "ERROR",
                    "Logs show a Kafka bootstrap/metadata failure. Check spring.kafka.bootstrap-servers against Compose.",
                    evidence,
                    null
            ));
        }
        return findings;
    }
}
