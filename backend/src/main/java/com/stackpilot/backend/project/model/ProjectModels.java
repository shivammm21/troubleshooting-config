package com.stackpilot.backend.project.model;

import java.util.List;
import java.util.Map;

/**
 * Immutable view of an indexed project.
 *
 * <p>The indexer records three kinds of facts:
 * <ul>
 *   <li>raw configuration bindings (host/port values found in YAML, properties and env files)</li>
 *   <li>the Compose topology (services, published ports, environment, dependencies)</li>
 *   <li>documentation facts extracted from README / docs markdown and the Dockerfile</li>
 * </ul>
 * Diagnostic rules combine these with live Docker state and container logs.
 */
public final class ProjectModels {

    private ProjectModels() {
    }

    public record IndexedFile(
            String relativePath,
            String kind,
            String content
    ) {
    }

    /** A single key/value pair found in a configuration file. */
    public record HostBinding(
            String file,
            String key,
            String value
    ) {
    }

    /**
     * A fact extracted from documentation so diagnostics can cite the intended
     * environment instead of guessing.
     *
     * @param source  relative path of the document
     * @param line    1-based line number inside {@code source}
     * @param kind    localhost-port | port-mention | env-var | service-mention | command | expose
     * @param value   the extracted value
     * @param context the trimmed source line, used as evidence excerpt
     */
    public record DocFact(
            String source,
            int line,
            String kind,
            String value,
            String context
    ) {
    }

    public record ServicePort(
            int hostPort,
            int containerPort,
            String protocol
    ) {
    }

    public record ComposeService(
            String name,
            String image,
            List<ServicePort> ports,
            Map<String, String> environment,
            List<String> dependsOn,
            List<String> networks,
            boolean hasHealthcheck,
            String file
    ) {
    }

    /**
     * A {@code ${VAR}} or {@code ${VAR:default}} placeholder found in configuration.
     */
    public record EnvPlaceholder(
            String file,
            String key,
            String variable,
            String fallback
    ) {
    }

    public record Snapshot(
            String id,
            String rootPath,
            List<IndexedFile> files,
            List<String> composeServices,
            List<HostBinding> redisHosts,
            List<HostBinding> kafkaBootstraps,
            List<HostBinding> databaseHosts,
            List<HostBinding> appPorts,
            List<EnvPlaceholder> envPlaceholders,
            List<String> envKeysPresent,
            List<ComposeService> services,
            List<DocFact> docFacts,
            List<String> notes
    ) {

        /** Convenience constructor used by tests and simple snapshots. */
        public Snapshot(
                String id,
                String rootPath,
                List<IndexedFile> files,
                List<String> composeServices,
                List<HostBinding> redisHosts,
                List<HostBinding> kafkaBootstraps
        ) {
            this(id, rootPath, files, composeServices, redisHosts, kafkaBootstraps,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        public SnapshotView view() {
            List<FileView> views = files.stream()
                    .map(file -> new FileView(
                            file.relativePath(),
                            file.kind(),
                            file.content() == null ? 0 : file.content().length()))
                    .toList();
            return new SnapshotView(
                    id,
                    rootPath,
                    views,
                    composeServices,
                    services,
                    docFacts,
                    envKeysPresent,
                    notes,
                    redisHosts.size(),
                    kafkaBootstraps.size(),
                    databaseHosts.size()
            );
        }
    }

    public record FileView(String relativePath, String kind, int bytes) {
    }

    /** Lightweight snapshot payload returned to the desktop UI (no file bodies). */
    public record SnapshotView(
            String id,
            String rootPath,
            List<FileView> files,
            List<String> composeServices,
            List<ComposeService> services,
            List<DocFact> docFacts,
            List<String> envKeysPresent,
            List<String> notes,
            int redisHostCount,
            int kafkaBootstrapCount,
            int databaseHostCount
    ) {
    }

    /** Persisted project reference so history survives a restart. */
    public record ProjectRef(String id, String rootPath) {
    }
}
