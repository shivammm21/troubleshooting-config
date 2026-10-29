package com.stackpilot.backend.docker;

import com.stackpilot.backend.docker.model.DockerModels;

import java.util.List;

/**
 * Abstraction over the Docker Engine API so tests can inject a fake.
 */
public interface DockerEngine {

    DockerModels.Status status();

    List<DockerModels.ContainerSummary> listContainers();

    String tailLogs(String containerId, int lines);

    /**
     * Reads live state with {@code docker inspect} (health, exit code, restart
     * count, compose labels). Returns null when the container cannot be inspected.
     */
    DockerModels.ContainerSummary inspect(String containerId);

    /** Restarts a container. Returns true when Docker accepted the command. */
    boolean restart(String containerId);

    /** Starts a stopped container. Returns true when Docker accepted the command. */
    boolean start(String containerId);
}
