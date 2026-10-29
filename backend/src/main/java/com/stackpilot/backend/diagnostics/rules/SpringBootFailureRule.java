package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.project.model.ProjectModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects a Spring Boot application that failed to start and tries to name the
 * concrete cause (bound port, missing placeholder, missing bean).
 */
@Component
public class SpringBootFailureRule implements DiagnosticRule {

    private static final Pattern FAILURE = Pattern.compile(
            "APPLICATION FAILED TO START|Web server failed to start|Port \\d+ was already in use"
                    + "|APPLICATION_FAILED_TO_START|Error starting ApplicationContext",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BOUND_PORT = Pattern.compile(
            "Port (\\d+) was already in use|Web server failed to start\\. Port (\\d+) was already in use",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "Could not resolve placeholder '([^']+)'", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CAUSE = Pattern.compile(
            "Caused by:\\s*([^\\n]{0,200})", Pattern.CASE_INSENSITIVE
    );

    @Override
    public String id() {
        return "spring-boot-startup-failure";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();

        for (DockerModels.ContainerSummary container : context.containers()) {
            String logs = context.logsByContainer().getOrDefault(container.id(), "");
            if (logs.isBlank() || !FAILURE.matcher(logs).find()) {
                continue;
            }

            context.claim("CRASH");
            String label = DiagnosticContext.label(container);
            List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
            evidence.add(RuleSupport.evidence("LOG", label, RuleSupport.excerpt(logs, FAILURE)));

            String causeLine = firstGroup(logs, CAUSE);
            if (!causeLine.isBlank()) {
                evidence.add(RuleSupport.evidence("LOG", label + " (Caused by)", causeLine));
            }

            String title = "Spring Boot failed to start";
            String summary;
            DiagnosticModels.RepairSuggestion repair = null;
            List<String> nextSteps = new ArrayList<>();
            String rootCauseKey = "spring-startup|" + container.name();

            Integer boundPort = firstInt(logs, BOUND_PORT);
            String placeholder = firstGroup(logs, PLACEHOLDER);

            if (boundPort != null) {
                context.claim("PORT");
                title = "Spring Boot cannot bind port " + boundPort;
                summary = "The application failed to start because port " + boundPort
                        + " is already in use inside the container. Either another process in the "
                        + "image owns the port or two services map the same container port.";
                nextSteps.add("Change server.port to a free port and restart the container");
                nextSteps.add("Or stop whichever process is holding port " + boundPort);

                List<ProjectModels.HostBinding> appPorts = context.project() == null
                        ? List.of()
                        : context.project().appPorts();
                if (!appPorts.isEmpty()) {
                    ProjectModels.HostBinding binding = appPorts.getFirst();
                    Set<Integer> taken = context.publishedHostPorts();
                    int free = RuleSupport.nextFreePort(boundPort + 1, taken);
                    repair = DiagnosticModels.RepairSuggestion.of(
                            "Move the server to port " + free + " in " + binding.file()
                                    + " and restart the container.",
                            List.of(new DiagnosticModels.FilePatch(binding.file(), binding.value(), String.valueOf(free))),
                            "MEDIUM",
                            true);
                }
                rootCauseKey = "spring-port|" + boundPort;
            } else if (!placeholder.isBlank()) {
                context.claim("CONFIG");
                title = "Missing configuration value: " + placeholder;
                summary = "Spring Boot could not resolve `${" + placeholder + "}` while starting, so the "
                        + "application context failed. Define the variable in the environment or in .env.";
                nextSteps.add("Add " + placeholder + " to " + context.envFileForWrites()
                        + " or to the service environment in docker-compose");
                nextSteps.add("Restart the app container after defining the value");
                rootCauseKey = "spring-placeholder|" + placeholder;

                boolean declared = context.declaredEnvKeys().stream()
                        .anyMatch(key -> key.equalsIgnoreCase(placeholder));
                if (declared) {
                    summary = "Spring Boot could not resolve `${" + placeholder + "}` even though the "
                            + "variable is declared. The running container most likely started before the "
                            + "value was added, so it needs a restart.";
                    nextSteps.add(0, "Restart the app container so it re-reads the environment");
                }
            } else {
                context.claim("CONFIG");
                summary = "The application context failed to start. The log excerpt above names the "
                        + "direct cause; the most common reasons are a bound port, a missing bean or an "
                        + "unreachable dependency.";
                nextSteps.add("Read the full stack trace: docker logs --tail 300 " + container.name());
                nextSteps.add("Fix the reported cause, then restart the container");
            }

            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    title,
                    "ERROR",
                    summary,
                    evidence,
                    repair,
                    90,
                    "runtime",
                    rootCauseKey,
                    List.copyOf(nextSteps)
            ));
        }

        return findings;
    }

    private static String firstGroup(String logs, Pattern pattern) {
        Matcher matcher = pattern.matcher(logs);
        return matcher.find() ? RuleSupport.truncate(matcher.group(1), 240) : "";
    }

    private static Integer firstInt(String logs, Pattern pattern) {
        Matcher matcher = pattern.matcher(logs);
        if (!matcher.find()) {
            return null;
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            if (value == null) {
                continue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // try the next capture group
            }
        }
        return null;
    }
}
