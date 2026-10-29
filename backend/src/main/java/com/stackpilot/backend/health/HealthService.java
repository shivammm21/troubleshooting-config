package com.stackpilot.backend.health;

import com.stackpilot.backend.docker.DockerEngine;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.health.model.HealthModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Feature 1 – Infrastructure Health Monitoring.
 *
 * <p>Combines the Docker engine state, per-container state/health (from
 * {@code docker inspect}) and active TCP probes against every published port.
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private static final String PROBE_HOST = "127.0.0.1";
    private static final int MAX_PROBES = 16;

    private final DockerEngine docker;
    private final ProbeService probes;

    public HealthService(DockerEngine docker, ProbeService probes) {
        this.docker = docker;
        this.probes = probes;
    }

    public HealthModels.Snapshot snapshot() {
        DockerModels.Status status = docker.status();
        List<DockerModels.ContainerSummary> containers = status.connected()
                ? docker.listContainers()
                : List.of();
        return snapshot(status, containers);
    }

    public HealthModels.Snapshot snapshot(DockerModels.Status status, List<DockerModels.ContainerSummary> containers) {
        List<HealthModels.Dependency> dependencies = dependencies(containers);

        int running = 0;
        int unhealthy = 0;
        int stopped = 0;
        List<String> issues = new ArrayList<>();

        for (DockerModels.ContainerSummary container : containers) {
            if (container.stopped()) {
                stopped++;
            } else if (container.running()) {
                running++;
            }
            if (container.unhealthy()) {
                unhealthy++;
            }
            if (container.stopped()) {
                issues.add("Container `" + container.name() + "` is " + container.state()
                        + (container.exitCode() == null ? "" : " (exit " + container.exitCode() + ")"));
            } else if (container.unhealthy()) {
                issues.add("Container `" + container.name() + "` reports unhealthy");
            } else if ("restarting".equalsIgnoreCase(container.state())) {
                issues.add("Container `" + container.name() + "` keeps restarting ("
                        + container.restartCount() + " restarts)");
            }
        }

        for (HealthModels.Dependency dependency : dependencies) {
            if (dependency.broken()) {
                issues.add(dependency.detail());
            }
        }

        String overall;
        if (!status.connected()) {
            overall = "DOWN";
            issues.add(0, "Docker engine unreachable: "
                    + (status.error() == null ? "no response" : status.error()));
        } else if (unhealthy > 0 || stopped > 0 || dependencies.stream().anyMatch(HealthModels.Dependency::broken)) {
            overall = "DEGRADED";
        } else {
            overall = "UP";
        }

        return new HealthModels.Snapshot(
                overall,
                status.connected(),
                status.engineName(),
                status.error(),
                containers.size(),
                running,
                unhealthy,
                stopped,
                containers,
                dependencies,
                List.copyOf(new LinkedHashSet<>(issues)),
                Instant.now()
        );
    }

    /** Probes every published port of every running container. */
    public List<HealthModels.Dependency> dependencies(List<DockerModels.ContainerSummary> containers) {
        List<HealthModels.Dependency> result = new ArrayList<>();
        Set<String> probed = new LinkedHashSet<>();

        for (DockerModels.ContainerSummary container : containers) {
            if (container.bindings() == null || container.bindings().isEmpty()) {
                continue;
            }
            // Probe only the first binding per container: it is the advertised entry point.
            DockerModels.PortBinding binding = container.bindings().stream()
                    .filter(b -> b.hostPort() > 0)
                    .findFirst()
                    .orElse(null);
            String service = displayName(container);

            if (binding == null) {
                continue;
            }

            String key = binding.hostPort() + "/" + binding.protocol();
            boolean shouldProbe = probed.size() < MAX_PROBES && probed.add(container.id() + key);
            HealthModels.TcpResult probe = null;
            if (shouldProbe && container.running()) {
                probe = probes.tcp(PROBE_HOST, binding.hostPort());
            }

            boolean reachable = probe != null && probe.reachable();
            String detail;
            if (!container.running()) {
                detail = service + " is not running (" + container.state() + ")";
            } else if (probe == null) {
                detail = service + " on port " + binding.hostPort() + " was not probed";
            } else if (reachable) {
                detail = service + " reachable on " + PROBE_HOST + ":" + binding.hostPort()
                        + " in " + probe.latencyMs() + "ms";
            } else {
                detail = service + " not reachable on " + PROBE_HOST + ":" + binding.hostPort()
                        + " — " + probe.detail();
            }

            result.add(new HealthModels.Dependency(
                    service,
                    container.name(),
                    container.state(),
                    container.health(),
                    binding.hostPort(),
                    binding.containerPort(),
                    reachable,
                    probe == null ? -1 : probe.latencyMs(),
                    detail
            ));
        }
        return result;
    }

    /**
     * Waits until the container is running and no longer reports {@code starting}.
     *
     * @return the last observed summary, or null when Docker could not inspect it
     */
    public DockerModels.ContainerSummary awaitHealthy(String containerId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        DockerModels.ContainerSummary last = null;
        while (System.currentTimeMillis() < deadline) {
            DockerModels.ContainerSummary current = docker.inspect(containerId);
            if (current != null) {
                last = current;
                String health = current.health() == null ? "none" : current.health().toLowerCase(Locale.ROOT);
                if (current.running() && !"starting".equals(health)) {
                    return current;
                }
                if (current.stopped()) {
                    return current;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
        }
        log.debug("Timed out waiting for container {} to become healthy", containerId);
        return last;
    }

    private static String displayName(DockerModels.ContainerSummary container) {
        if (container.composeService() != null && !container.composeService().isBlank()) {
            return container.composeService();
        }
        return container.name();
    }
}
