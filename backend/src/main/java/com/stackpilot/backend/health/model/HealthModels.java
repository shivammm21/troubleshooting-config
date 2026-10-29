package com.stackpilot.backend.health.model;

import com.stackpilot.backend.docker.model.DockerModels;

import java.time.Instant;
import java.util.List;

/**
 * Infrastructure health snapshot: Docker engine state, container states and
 * active reachability probes for every published port.
 */
public final class HealthModels {

    private HealthModels() {
    }

    public record TcpResult(boolean reachable, long latencyMs, String detail) {
    }

    /**
     * A dependency that the stack advertises on the host.
     *
     * @param service        compose service or container name
     * @param container      container name
     * @param containerState running | exited | ...
     * @param health         healthy | unhealthy | starting | none
     * @param hostPort       published host port that was probed
     * @param containerPort  container-side port
     * @param reachable      whether the TCP probe succeeded
     * @param latencyMs      probe latency, -1 when the probe failed
     * @param detail         human readable probe result
     */
    public record Dependency(
            String service,
            String container,
            String containerState,
            String health,
            Integer hostPort,
            Integer containerPort,
            boolean reachable,
            long latencyMs,
            String detail
    ) {
        public boolean broken() {
            boolean containerDown = containerState != null
                    && !containerState.equalsIgnoreCase("running");
            return containerDown || "unhealthy".equalsIgnoreCase(health) || !reachable;
        }
    }

    /**
     * @param overall UP | DEGRADED | DOWN
     */
    public record Snapshot(
            String overall,
            boolean dockerConnected,
            String engineName,
            String dockerError,
            int totalContainers,
            int runningContainers,
            int unhealthyContainers,
            int stoppedContainers,
            List<DockerModels.ContainerSummary> containers,
            List<Dependency> dependencies,
            List<String> issues,
            Instant checkedAt
    ) {
    }
}
