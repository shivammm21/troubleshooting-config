package com.stackpilot.backend.project.model;

import java.util.List;

public final class ProjectModels {

    private ProjectModels() {
    }

    public record IndexedFile(
            String relativePath,
            String kind,
            String content
    ) {
    }

    public record HostBinding(
            String file,
            String key,
            String value
    ) {
    }

    public record Snapshot(
            String id,
            String rootPath,
            List<IndexedFile> files,
            List<String> composeServices,
            List<HostBinding> redisHosts,
            List<HostBinding> kafkaBootstraps
    ) {
    }
}
