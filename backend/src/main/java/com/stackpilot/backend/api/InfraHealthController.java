package com.stackpilot.backend.api;

import com.stackpilot.backend.docker.DockerEngine;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.health.HealthService;
import com.stackpilot.backend.health.model.HealthModels;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Feature 1 – Infrastructure Health Monitoring.
 *
 * <p>One call returns the engine state, every container's state/health/exit code
 * and the result of a live TCP probe against every published port. This is the
 * data the desktop health panel renders and the baseline used by post-fix
 * verification.
 */
@RestController
@RequestMapping("/api/infra")
public class InfraHealthController {

    private final HealthService health;
    private final DockerEngine docker;

    public InfraHealthController(HealthService health, DockerEngine docker) {
        this.health = health;
        this.docker = docker;
    }

    @GetMapping("/health")
    public HealthModels.Snapshot health() {
        return health.snapshot();
    }

    /** Containers only, ordered the way the UI shows them (broken first). */
    @GetMapping("/containers")
    public List<DockerModels.ContainerSummary> containers() {
        return docker.listContainers().stream()
                .sorted((a, b) -> Integer.compare(weight(a), weight(b)))
                .toList();
    }

    private static int weight(DockerModels.ContainerSummary container) {
        if (container.stopped()) {
            return 0;
        }
        if (container.unhealthy()) {
            return 1;
        }
        if ("restarting".equalsIgnoreCase(container.state())) {
            return 2;
        }
        return 3;
    }
}
