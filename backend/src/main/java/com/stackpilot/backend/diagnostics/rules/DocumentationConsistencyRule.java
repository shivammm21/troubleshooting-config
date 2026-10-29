package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.project.model.ProjectModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Feature 4 – Documentation-Aware Diagnostics.
 *
 * <p>Compares what the project's own documentation promises (README ports, service
 * names, required environment variables) with what the Compose/Spring configuration
 * actually does. Every finding cites the documentation line so the developer can
 * check the expected setup themselves.
 */
@Component
public class DocumentationConsistencyRule implements DiagnosticRule {

    private static final int MAX_FINDINGS = 6;

    /** Service names that are meaningful to compare against Compose. */
    private static final Set<String> KNOWN_SERVICES = Set.of(
            "redis", "kafka", "zookeeper", "postgres", "postgresql", "mysql", "mariadb",
            "mongo", "mongodb", "rabbitmq", "elasticsearch", "api", "backend", "web",
            "frontend", "app", "server", "gateway"
    );

    @Override
    public String id() {
        return "documentation-mismatch";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String category() {
        return "documentation";
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        if (context.project() == null || context.project().docFacts().isEmpty()) {
            return findings;
        }

        addPortDrift(context, findings);
        addMissingDocumentedVariables(context, findings);
        addDocumentedButMissingServices(context, findings);

        return findings.size() > MAX_FINDINGS ? findings.subList(0, MAX_FINDINGS) : findings;
    }

    /** README says localhost:8080 while Compose publishes 8081 -> 8080. */
    private void addPortDrift(DiagnosticContext context, List<DiagnosticModels.Finding> findings) {
        Set<Integer> published = context.publishedHostPorts();
        if (published.isEmpty()) {
            return;
        }
        Set<Integer> containerPorts = context.containerPorts();
        Set<Integer> reported = new LinkedHashSet<>();

        for (ProjectModels.DocFact fact : context.docFacts("localhost-port")) {
            Integer documented = parseInt(fact.value());
            if (documented == null || published.contains(documented) || !containerPorts.contains(documented)) {
                continue;
            }
            if (!reported.add(documented)) {
                continue;
            }
            Optional<ProjectModels.ServicePort> mapping = context.services().stream()
                    .flatMap(service -> service.ports().stream())
                    .filter(port -> port.containerPort() == documented && port.hostPort() > 0)
                    .findFirst();
            if (mapping.isEmpty()) {
                continue;
            }
            ProjectModels.ServicePort port = mapping.get();

            List<DiagnosticModels.Evidence> evidence = List.of(
                    RuleSupport.evidence("DOC", fact.source() + ":" + fact.line(), fact.context()),
                    RuleSupport.evidence("CONFIG", "docker-compose ports",
                            port.hostPort() + ":" + port.containerPort()));

            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Documentation and Compose publish different ports",
                    "WARN",
                    fact.source() + " tells developers to use localhost:" + documented + ", but Compose "
                            + "publishes the container port on host port " + port.hostPort()
                            + ". Anyone following the README will reach the wrong endpoint.",
                    evidence,
                    null,
                    75,
                    "documentation",
                    "doc-port|" + documented,
                    List.of(
                            "Either publish the service on host port " + documented + " in the Compose file",
                            "Or update " + fact.source() + " line " + fact.line() + " to localhost:"
                                    + port.hostPort())
            ));
        }
    }

    /** A variable described in the docs that nothing defines. */
    private void addMissingDocumentedVariables(DiagnosticContext context, List<DiagnosticModels.Finding> findings) {
        Set<String> declared = new LinkedHashSet<>();
        context.declaredEnvKeys().forEach(key -> declared.add(key.toUpperCase(Locale.ROOT)));
        Set<String> reported = new LinkedHashSet<>();

        for (ProjectModels.DocFact fact : context.docFacts("env-var")) {
            String variable = fact.value().toUpperCase(Locale.ROOT);
            if (declared.contains(variable) || reported.contains(variable)) {
                continue;
            }
            if (context.claimed("ENV:" + variable)) {
                continue;
            }
            if (!isStrongVariable(variable)) {
                continue;
            }
            reported.add(variable);

            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Documented variable is not configured: " + variable,
                    "WARN",
                    fact.source() + " documents " + variable + " as part of the intended environment, but "
                            + "no .env file or Compose service defines it. A fresh checkout will start "
                            + "without it and fail the same way this documentation warns about.",
                    List.of(RuleSupport.evidence("DOC", fact.source() + ":" + fact.line(), fact.context())),
                    null,
                    65,
                    "documentation",
                    "doc-env|" + variable,
                    List.of(
                            "Add " + variable + " to " + context.envFileForWrites(),
                            "Or declare it under the app service environment in docker-compose",
                            "Restart the stack so the container receives the value")
            ));
        }
    }

    /** The README describes a service that Compose does not define. */
    private void addDocumentedButMissingServices(DiagnosticContext context, List<DiagnosticModels.Finding> findings) {
        List<String> services = context.composeServices();
        if (services.isEmpty()) {
            return;
        }
        Set<String> reported = new LinkedHashSet<>();

        for (ProjectModels.DocFact fact : context.docFacts("service-mention")) {
            String name = fact.value() == null ? "" : fact.value().toLowerCase(Locale.ROOT);
            if (!KNOWN_SERVICES.contains(name) || reported.contains(name)) {
                continue;
            }
            if (context.composeServiceContaining(name).isPresent()) {
                continue;
            }
            reported.add(name);

            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Documented service `" + name + "` is missing from Compose",
                    "WARN",
                    fact.source() + " refers to a `" + name + "` service, but Compose only defines "
                            + String.join(", ", services) + ". The documented topology and the runnable "
                            + "topology disagree.",
                    List.of(
                            RuleSupport.evidence("DOC", fact.source() + ":" + fact.line(), fact.context()),
                            RuleSupport.evidence("CONFIG", "docker-compose services", String.join(", ", services))),
                    null,
                    55,
                    "documentation",
                    "doc-service|" + name,
                    List.of(
                            "Add the `" + name + "` service to the Compose file, or",
                            "Update " + fact.source() + " so it matches the services that actually exist")
            ));
        }
    }

    private static boolean isStrongVariable(String variable) {
        return variable.endsWith("_HOST") || variable.endsWith("_PORT") || variable.endsWith("_URL")
                || variable.endsWith("_USERNAME") || variable.endsWith("_PASSWORD")
                || variable.endsWith("_SECRET") || variable.endsWith("_TOKEN")
                || variable.startsWith("SPRING_") || variable.startsWith("REDIS_")
                || variable.startsWith("KAFKA_") || variable.startsWith("POSTGRES_")
                || variable.startsWith("MYSQL_") || variable.startsWith("MONGO_")
                || variable.startsWith("DATABASE_");
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
