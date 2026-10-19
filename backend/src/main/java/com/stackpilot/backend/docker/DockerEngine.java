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
}
