package com.stackpilot.backend.docker.model;

import java.util.List;

public final class DockerModels {

    private DockerModels() {
    }

    public record Status(
            boolean connected,
            String engineName,
            int containerCount,
            String error
    ) {
    }

    public record ContainerSummary(
            String id,
            String name,
            String image,
            String state,
            String status,
            List<String> ports
    ) {
    }
}
