package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.project.model.ProjectModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds {@code ${VAR}} placeholders that are never defined, and can write the
 * missing value into {@code .env} when the right value is derivable from the
 * Compose topology.
 */
@Component
public class MissingEnvVarRule implements DiagnosticRule {

    /** Variables that can be derived from a Compose service name. */
    private record Inference(String variableHint, String serviceHint) {
    }

    private static final List<Inference> INFERENCES = List.of(
            new Inference("REDIS_HOST", "redis"),
            new Inference("REDIS_HOSTNAME", "redis"),
            new Inference("KAFKA_BOOTSTRAP", "kafka"),
            new Inference("BOOTSTRAP_SERVERS", "kafka"),
            new Inference("POSTGRES", "postgres"),
            new Inference("DATASOURCE_URL", "postgres"),
            new Inference("MYSQL", "mysql"),
            new Inference("MONGO", "mongo"),
            new Inference("RABBIT", "rabbit")
    );

    @Override
    public String id() {
        return "missing-env-var";
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        if (context.project() == null) {
            return findings;
        }

        Set<String> declared = new LinkedHashSet<>();
        context.declaredEnvKeys().forEach(key -> declared.add(key.toUpperCase(Locale.ROOT)));

        Map<String, List<ProjectModels.EnvPlaceholder>> byVariable = new LinkedHashMap<>();
        for (ProjectModels.EnvPlaceholder placeholder : context.project().envPlaceholders()) {
            if (placeholder.variable() == null || placeholder.variable().isBlank()) {
                continue;
            }
            byVariable.computeIfAbsent(placeholder.variable(), key -> new ArrayList<>()).add(placeholder);
        }

        for (Map.Entry<String, List<ProjectModels.EnvPlaceholder>> entry : byVariable.entrySet()) {
            String variable = entry.getKey();
            String upper = variable.toUpperCase(Locale.ROOT);
            if (declared.contains(upper)) {
                continue;
            }
            List<ProjectModels.EnvPlaceholder> placeholders = entry.getValue();
            boolean hasFallback = placeholders.stream()
                    .anyMatch(placeholder -> placeholder.fallback() != null && !placeholder.fallback().isBlank());
            if (hasFallback) {
                // A default is present, so this is not a failure yet.
                continue;
            }

            context.claim("CONFIG");
            context.claim("ENV:" + upper);

            List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
            for (ProjectModels.EnvPlaceholder placeholder : placeholders) {
                evidence.add(RuleSupport.evidence("CONFIG",
                        placeholder.file() + " → " + placeholder.key(),
                        "${" + placeholder.variable() + "} is not defined"));
            }

            String envFile = context.envFileForWrites();
            String inferredValue = inferValue(context, upper);
            DiagnosticModels.RepairSuggestion repair = null;
            List<String> nextSteps = new ArrayList<>();

            if (inferredValue != null) {
                repair = DiagnosticModels.RepairSuggestion.of(
                        "Define " + variable + "=" + inferredValue + " in " + envFile
                                + " (derived from the Compose service) and restart the app container.",
                        List.of(DiagnosticModels.FilePatch.append(envFile, variable + "=" + inferredValue)),
                        "LOW",
                        true);
                nextSteps.add("Apply the patch to " + envFile);
                nextSteps.add("Recreate the container so the new environment is loaded (docker compose up -d)");
            } else {
                nextSteps.add("Add " + variable + " to " + envFile);
                nextSteps.add("Set the real value (credential, URL or name) before starting the app");
                nextSteps.add("Restart the app container after defining the variable");
            }

            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Undefined configuration variable: " + variable,
                    hasFallback ? "WARN" : "ERROR",
                    "Configuration references `${" + variable + "}` but no .env file or Compose service "
                            + "defines it. Spring Boot fails at startup with "
                            + "\"Could not resolve placeholder '" + variable + "'\".",
                    evidence,
                    repair,
                    88,
                    "configuration",
                    "env:" + upper,
                    List.copyOf(nextSteps)
            ));
        }
        return findings;
    }

    private static String inferValue(DiagnosticContext context, String variable) {
        for (Inference inference : INFERENCES) {
            if (!variable.contains(inference.variableHint())) {
                continue;
            }
            Optional<String> service = context.composeServiceContaining(inference.serviceHint());
            if (service.isPresent()) {
                if (variable.endsWith("_URL")) {
                    return "jdbc:postgresql://" + service.get() + ":5432/app";
                }
                if (variable.contains("KAFKA_BOOTSTRAP") || variable.contains("BOOTSTRAP_SERVERS")) {
                    return service.get() + ":9092";
                }
                if (variable.contains("PORT")) {
                    return "5432";
                }
                return service.get();
            }
        }
        return null;
    }
}
