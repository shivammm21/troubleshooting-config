package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class SpringBootFailureRule implements DiagnosticRule {

    private static final Pattern FAILURE = Pattern.compile(
            "APPLICATION FAILED TO START|Web server failed to start|Port \\d+ was already in use|APPLICATION_FAILED_TO_START",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public String id() {
        return "spring-boot-startup-failure";
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        context.logsByContainer().forEach((container, logs) -> {
            if (!FAILURE.matcher(logs).find()) {
                return;
            }
            findings.add(new DiagnosticModels.Finding(
                    UUID.randomUUID().toString(),
                    id(),
                    "Spring Boot failed to start",
                    "ERROR",
                    "Container `" + container + "` logged APPLICATION FAILED TO START. Use the log excerpt as evidence; the usual causes are a bound port, missing bean, or unreachable dependency.",
                    List.of(new DiagnosticModels.Evidence("LOG", container, RedisHostnameMismatchRule.excerpt(logs, FAILURE))),
                    null
            ));
        });
        return findings;
    }
}
