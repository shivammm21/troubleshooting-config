package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.health.model.HealthModels;
import com.stackpilot.backend.logs.model.LogModels;
import com.stackpilot.backend.project.model.ProjectModels;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything a rule needs: the indexed project, live Docker state, container
 * logs (raw + analysed) and the results of active reachability probes.
 *
 * <p>{@link #claims()} lets specific rules mark a failure category as explained
 * so the generic log rule does not report the same root cause twice.
 */
public record DiagnosticContext(
        ProjectModels.Snapshot project,
        DockerModels.Status dockerStatus,
        List<DockerModels.ContainerSummary> containers,
        Map<String, String> logsByContainer,
        Map<String, LogModels.Summary> logSummaries,
        List<HealthModels.Dependency> dependencies,
        Set<String> claims
) {

    public DiagnosticContext(
            ProjectModels.Snapshot project,
            DockerModels.Status dockerStatus,
            List<DockerModels.ContainerSummary> containers,
            Map<String, String> logsByContainer
    ) {
        this(project, dockerStatus, containers, logsByContainer,
                Map.of(), List.of(), new LinkedHashSet<>());
    }

    public DiagnosticContext {
        containers = containers == null ? List.of() : List.copyOf(containers);
        logsByContainer = logsByContainer == null ? Map.of() : Map.copyOf(logsByContainer);
        logSummaries = logSummaries == null ? Map.of() : Map.copyOf(logSummaries);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        claims = claims == null ? new LinkedHashSet<>() : claims;
    }

    // ------------------------------------------------------------------
    // logs
    // ------------------------------------------------------------------

    public String combinedLogs() {
        return logsByContainer.values().stream().collect(Collectors.joining("\n"));
    }

    public LogModels.Summary summaryFor(DockerModels.ContainerSummary container) {
        if (container == null) {
            return LogModels.Summary.empty();
        }
        LogModels.Summary summary = logSummaries.get(container.id());
        return summary == null ? LogModels.Summary.empty() : summary;
    }

    public List<LogModels.Summary> allSummaries() {
        return List.copyOf(logSummaries.values());
    }

    public int totalLogLines() {
        return logSummaries.values().stream().mapToInt(LogModels.Summary::totalLines).sum();
    }

    public int totalErrorLines() {
        return logSummaries.values().stream().mapToInt(LogModels.Summary::errorLines).sum();
    }

    public List<String> topSignatures(int limit) {
        return logSummaries.values().stream()
                .flatMap(summary -> summary.signatures().stream())
                .filter(signature -> "ERROR".equals(signature.severity()))
                .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                .limit(limit)
                .map(LogModels.Signature::signature)
                .toList();
    }

    // ------------------------------------------------------------------
    // containers
    // ------------------------------------------------------------------

    public List<DockerModels.ContainerSummary> runningContainers() {
        return containers.stream().filter(DockerModels.ContainerSummary::running).toList();
    }

    public Optional<DockerModels.ContainerSummary> containerByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String needle = name.toLowerCase(Locale.ROOT);
        return containers.stream()
                .filter(container -> matches(container, needle))
                .findFirst();
    }

    public Optional<DockerModels.ContainerSummary> primaryAppContainer() {
        return containers.stream()
                .filter(container -> !container.infrastructure())
                .findFirst();
    }

    public List<DockerModels.ContainerSummary> appContainers() {
        return containers.stream().filter(container -> !container.infrastructure()).toList();
    }

    private static boolean matches(DockerModels.ContainerSummary container, String needle) {
        return (container.name() != null && container.name().toLowerCase(Locale.ROOT).equals(needle))
                || (container.name() != null && container.name().toLowerCase(Locale.ROOT).contains(needle))
                || (container.composeService() != null
                && container.composeService().toLowerCase(Locale.ROOT).contains(needle));
    }

    public static String label(DockerModels.ContainerSummary container) {
        if (container == null) {
            return "unknown";
        }
        String id = container.id() == null ? "" : container.id();
        String shortId = id.length() > 12 ? id.substring(0, 12) : id;
        return (container.name() == null || container.name().isBlank())
                ? shortId
                : container.name() + " (" + shortId + ")";
    }

    // ------------------------------------------------------------------
    // project topology
    // ------------------------------------------------------------------

    public boolean hasProject() {
        return project != null;
    }

    public List<String> composeServices() {
        return project == null ? List.of() : project.composeServices();
    }

    public boolean composeHasService(String hint) {
        if (project == null || hint == null) {
            return false;
        }
        String needle = hint.toLowerCase(Locale.ROOT);
        return project.composeServices().stream()
                .anyMatch(name -> name.toLowerCase(Locale.ROOT).contains(needle));
    }

    public Optional<String> composeServiceContaining(String hint) {
        if (project == null || hint == null) {
            return Optional.empty();
        }
        String needle = hint.toLowerCase(Locale.ROOT);
        return project.composeServices().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(needle))
                .findFirst();
    }

    public List<ProjectModels.ComposeService> services() {
        return project == null ? List.of() : project.services();
    }

    public Optional<ProjectModels.ComposeService> serviceNamed(String name) {
        if (project == null || name == null) {
            return Optional.empty();
        }
        String needle = name.toLowerCase(Locale.ROOT);
        return project.services().stream()
                .filter(service -> service.name().toLowerCase(Locale.ROOT).equals(needle))
                .findFirst();
    }

    public boolean hasNonInfraAppService() {
        if (project == null) {
            return false;
        }
        return project.composeServices().stream().anyMatch(name -> {
            String n = name.toLowerCase(Locale.ROOT);
            return !n.contains("redis") && !n.contains("kafka") && !n.contains("zookeeper")
                    && !n.contains("postgres") && !n.contains("mysql") && !n.contains("mongo")
                    && !n.contains("rabbit") && !n.contains("elastic");
        });
    }

    /** Published host ports across every compose service. */
    public Set<Integer> publishedHostPorts() {
        Set<Integer> ports = new LinkedHashSet<>();
        services().forEach(service -> service.ports().forEach(port -> {
            if (port.hostPort() > 0) {
                ports.add(port.hostPort());
            }
        }));
        return ports;
    }

    public Set<Integer> containerPorts() {
        Set<Integer> ports = new LinkedHashSet<>();
        services().forEach(service -> service.ports().forEach(port -> {
            if (port.containerPort() > 0) {
                ports.add(port.containerPort());
            }
        }));
        return ports;
    }

    public Set<String> declaredEnvKeys() {
        if (project == null) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>(project.envKeysPresent());
        services().forEach(service -> keys.addAll(service.environment().keySet()));
        return keys;
    }

    public Optional<ProjectModels.IndexedFile> file(String relativePath) {
        if (project == null || relativePath == null) {
            return Optional.empty();
        }
        return project.files().stream()
                .filter(file -> file.relativePath().equals(relativePath))
                .findFirst();
    }

    /** Preferred env file for appending new variables. */
    public String envFileForWrites() {
        if (project == null) {
            return ".env";
        }
        return project.files().stream()
                .map(ProjectModels.IndexedFile::relativePath)
                .filter(path -> path.equals(".env") || path.endsWith("/.env"))
                .findFirst()
                .orElse(".env");
    }

    public List<ProjectModels.DocFact> docFacts(String kind) {
        if (project == null) {
            return List.of();
        }
        return project.docFacts().stream()
                .filter(fact -> fact.kind().equals(kind))
                .toList();
    }

    // ------------------------------------------------------------------
    // runtime probes
    // ------------------------------------------------------------------

    public Optional<HealthModels.Dependency> dependencyFor(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return Optional.empty();
        }
        String needle = serviceName.toLowerCase(Locale.ROOT);
        return dependencies.stream()
                .filter(dependency -> {
                    String service = dependency.service() == null ? "" : dependency.service().toLowerCase(Locale.ROOT);
                    String container = dependency.container() == null ? "" : dependency.container().toLowerCase(Locale.ROOT);
                    return service.contains(needle) || container.contains(needle);
                })
                .findFirst();
    }

    public List<HealthModels.Dependency> brokenDependencies() {
        return dependencies.stream().filter(HealthModels.Dependency::broken).toList();
    }

    // ------------------------------------------------------------------
    // claim bookkeeping
    // ------------------------------------------------------------------

    public void claim(String key) {
        if (key != null && !key.isBlank()) {
            claims.add(key.toUpperCase(Locale.ROOT));
        }
    }

    public boolean claimed(String key) {
        return key != null && claims.contains(key.toUpperCase(Locale.ROOT));
    }

    public List<String> claimList() {
        return List.copyOf(claims);
    }

    public Map<String, String> logsByReadableName() {
        Map<String, String> result = new LinkedHashMap<>();
        for (DockerModels.ContainerSummary container : containers) {
            String raw = logsByContainer.get(container.id());
            if (raw != null) {
                result.put(label(container), raw);
            }
        }
        return result;
    }
}
