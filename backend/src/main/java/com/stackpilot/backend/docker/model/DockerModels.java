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

    /** A published port mapping: host:hostPort -> containerPort. */
    public record PortBinding(
            int hostPort,
            int containerPort,
            String hostIp,
            String protocol
    ) {
    }

    /**
     * Enriched container summary.
     *
     * @param health         healthy | unhealthy | starting | none | exited | unknown
     * @param exitCode       last exit code, null while the container is running
     * @param restartCount   how many times Docker has restarted this container
     * @param composeProject value of the {@code com.docker.compose.project} label
     * @param composeService value of the {@code com.docker.compose.service} label
     * @param bindings       structurally parsed port mappings
     * @param startedAt      ISO-8601 start time reported by Docker
     * @param error          last error string reported by Docker, if any
     */
    public record ContainerSummary(
            String id,
            String name,
            String image,
            String state,
            String status,
            List<String> ports,
            String health,
            Integer exitCode,
            int restartCount,
            String composeProject,
            String composeService,
            List<PortBinding> bindings,
            String startedAt,
            String error
    ) {

        /** Backwards-compatible constructor for simple fakes and tests. */
        public ContainerSummary(
                String id,
                String name,
                String image,
                String state,
                String status,
                List<String> ports
        ) {
            this(id, name, image, state, status, ports, "none", null, 0, "", "", List.of(), "", "");
        }

        public boolean running() {
            return state != null && state.equalsIgnoreCase("running");
        }

        public boolean unhealthy() {
            return "unhealthy".equalsIgnoreCase(health);
        }

        public boolean stopped() {
            return state != null
                    && (state.equalsIgnoreCase("exited") || state.equalsIgnoreCase("dead")
                    || state.equalsIgnoreCase("created"));
        }

        public boolean infrastructure() {
            String n = (name == null ? "" : name).toLowerCase();
            String s = (composeService == null ? "" : composeService).toLowerCase();
            return n.contains("redis") || n.contains("kafka") || n.contains("zookeeper")
                    || n.contains("postgres") || n.contains("mysql") || n.contains("mongo")
                    || n.contains("rabbit") || n.contains("elastic") || s.contains("redis")
                    || s.contains("kafka") || s.contains("zookeeper") || s.contains("postgres")
                    || s.contains("mysql") || s.contains("mongo") || s.contains("rabbit");
        }
    }
}
