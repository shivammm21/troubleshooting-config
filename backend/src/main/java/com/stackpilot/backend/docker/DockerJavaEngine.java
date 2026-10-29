package com.stackpilot.backend.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.stackpilot.backend.docker.model.DockerModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
public class DockerJavaEngine implements DockerEngine {

    private static final Logger log = LoggerFactory.getLogger(DockerJavaEngine.class);

    /** Inspections are cached briefly so UI polling does not hammer the daemon. */
    private static final long INSPECT_TTL_MS = 2_000L;

    private volatile DockerClient client;

    private final Map<String, CacheEntry> inspectCache = new ConcurrentHashMap<>();

    private record CacheEntry(DockerModels.ContainerSummary summary, long at) {
    }

    private synchronized DockerClient client() {
        if (client == null) {
            var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            var http = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(20)
                    .connectionTimeout(Duration.ofSeconds(3))
                    .responseTimeout(Duration.ofSeconds(20))
                    .build();
            client = DockerClientImpl.getInstance(config, http);
            log.info("Docker client configured for {}", config.getDockerHost());
        }
        return client;
    }

    @Override
    public DockerModels.Status status() {
        try {
            Info info = client().infoCmd().exec();
            int count = info.getContainers() == null ? 0 : info.getContainers();
            return new DockerModels.Status(true, info.getName(), count, null);
        } catch (Exception e) {
            log.debug("Docker engine not reachable: {}", e.getMessage());
            return new DockerModels.Status(false, null, 0, e.getMessage());
        }
    }

    @Override
    public List<DockerModels.ContainerSummary> listContainers() {
        try {
            List<Container> containers = client().listContainersCmd().withShowAll(true).exec();
            List<DockerModels.ContainerSummary> result = new ArrayList<>();
            for (Container container : containers) {
                result.add(enrich(container));
            }
            return result;
        } catch (Exception e) {
            log.debug("Failed to list containers: {}", e.getMessage());
            return List.of();
        }
    }

    private DockerModels.ContainerSummary enrich(Container container) {
        String id = container.getId();
        String name = firstName(container.getNames());
        String state = container.getState() == null ? "" : container.getState();
        String status = container.getStatus() == null ? "" : container.getStatus();

        CacheEntry cached = inspectCache.get(id);
        if (cached != null && System.currentTimeMillis() - cached.at() < INSPECT_TTL_MS) {
            DockerModels.ContainerSummary hit = cached.summary();
            // The cheap fields come from the list call and are always fresh.
            return new DockerModels.ContainerSummary(
                    hit.id(), name, container.getImage(), state, status,
                    hit.ports(), hit.health(), hit.exitCode(), hit.restartCount(),
                    hit.composeProject(), hit.composeService(), hit.bindings(), hit.startedAt(), hit.error());
        }

        String health = deriveHealth(status);
        Integer exitCode = null;
        int restartCount = 0;
        String composeProject = "";
        String composeService = "";
        String startedAt = "";
        String error = "";
        List<DockerModels.PortBinding> bindings = bindings(container.getPorts());

        try {
            InspectContainerResponse inspect = client().inspectContainerCmd(id).exec();
            Object inspectState = call(inspect, "getState");
            if (inspectState != null) {
                String inspectStatus = asString(call(inspectState, "getStatus"));
                if (inspectStatus != null && !inspectStatus.isBlank()) {
                    state = inspectStatus;
                }
                Object healthNode = call(inspectState, "getHealth");
                if (healthNode != null) {
                    String reported = asString(call(healthNode, "getStatus"));
                    if (reported != null && !reported.isBlank()) {
                        health = reported.toLowerCase(Locale.ROOT);
                    }
                }
                exitCode = asInt(call(inspectState, "getExitCode"));
                Integer restarts = asInt(call(inspectState, "getRestartCount"));
                if (restarts != null) {
                    restartCount = restarts;
                }
                String started = asString(call(inspectState, "getStartedAt"));
                if (started != null) {
                    startedAt = started;
                }
                String stateError = asString(call(inspectState, "getError"));
                if (stateError != null) {
                    error = stateError;
                }
            }
            Object config = call(inspect, "getConfig");
            Object labelsNode = config == null ? null : call(config, "getLabels");
            if (labelsNode instanceof Map<?, ?> labels) {
                composeProject = label(labels, "com.docker.compose.project");
                composeService = label(labels, "com.docker.compose.service");
            }
            List<DockerModels.PortBinding> inspected = inspectedBindings(inspect);
            if (!inspected.isEmpty()) {
                bindings = inspected;
            }
        } catch (Exception e) {
            log.debug("docker inspect failed for {}: {}", id, e.getMessage());
        }

        if (exitCode != null && exitCode == 0 && !"exited".equalsIgnoreCase(state)) {
            exitCode = null;
        }

        DockerModels.ContainerSummary summary = new DockerModels.ContainerSummary(
                id,
                name,
                container.getImage(),
                state,
                status,
                portStrings(bindings),
                health,
                exitCode,
                restartCount,
                composeProject,
                composeService,
                bindings,
                startedAt,
                error
        );
        inspectCache.put(id, new CacheEntry(summary, System.currentTimeMillis()));
        return summary;
    }

    @Override
    public DockerModels.ContainerSummary inspect(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return null;
        }
        inspectCache.remove(containerId);
        try {
            InspectContainerResponse inspect = client().inspectContainerCmd(containerId).exec();
            String name = "";
            try {
                name = inspect.getName() == null ? "" : inspect.getName().replaceFirst("^/", "");
            } catch (Exception ignored) {
                // name is best-effort
            }
            Object inspectState = call(inspect, "getState");
            String state = asString(call(inspectState, "getStatus"));
            Object healthNode = call(inspectState, "getHealth");
            String health = healthNode == null ? "none" : asString(call(healthNode, "getStatus"));
            Integer exitCode = asInt(call(inspectState, "getExitCode"));
            Integer restarts = asInt(call(inspectState, "getRestartCount"));
            String startedAt = asString(call(inspectState, "getStartedAt"));
            String stateError = asString(call(inspectState, "getError"));
            Object config = call(inspect, "getConfig");
            String image = "";
            Object imageNode = config == null ? null : call(config, "getImage");
            if (imageNode instanceof String s) {
                image = s;
            }
            Object labelsNode = config == null ? null : call(config, "getLabels");
            String composeProject = "";
            String composeService = "";
            if (labelsNode instanceof Map<?, ?> labels) {
                composeProject = label(labels, "com.docker.compose.project");
                composeService = label(labels, "com.docker.compose.service");
            }
            List<DockerModels.PortBinding> bindings = inspectedBindings(inspect);
            return new DockerModels.ContainerSummary(
                    containerId,
                    name,
                    image,
                    state == null ? "" : state,
                    state == null ? "" : state,
                    portStrings(bindings),
                    health == null ? "none" : health.toLowerCase(Locale.ROOT),
                    exitCode,
                    restarts == null ? 0 : restarts,
                    composeProject,
                    composeService,
                    bindings,
                    startedAt == null ? "" : startedAt,
                    stateError == null ? "" : stateError
            );
        } catch (Exception e) {
            log.debug("docker inspect failed for {}: {}", containerId, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean restart(String containerId) {
        try {
            client().restartContainerCmd(containerId).exec();
            inspectCache.remove(containerId);
            log.info("Restarted container {}", containerId);
            return true;
        } catch (Exception e) {
            log.warn("Failed to restart container {}: {}", containerId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean start(String containerId) {
        try {
            client().startContainerCmd(containerId).exec();
            inspectCache.remove(containerId);
            log.info("Started container {}", containerId);
            return true;
        } catch (Exception e) {
            log.warn("Failed to start container {}: {}", containerId, e.getMessage());
            return false;
        }
    }

    @Override
    public String tailLogs(String containerId, int lines) {
        StringBuilder output = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        try {
            client().logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(lines)
                    .withTimestamps(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            if (frame.getPayload() == null) {
                                return;
                            }
                            // docker-java delivers the raw multiplexed stream. Each frame already
                            // has the 8-byte stream header stripped when using the typed callback,
                            // but the payload may still start with a non-printable byte on some
                            // Docker versions/transports. Strip any leading control bytes (< 0x20
                            // and not TAB/LF/CR) that are not part of the actual log text.
                            byte[] payload = frame.getPayload();
                            int start = 0;
                            if (payload.length > 8
                                    && (payload[0] == 0 || payload[0] == 1 || payload[0] == 2)
                                    && payload[1] == 0 && payload[2] == 0 && payload[3] == 0) {
                                int declared = ((payload[4] & 0xFF) << 24)
                                        | ((payload[5] & 0xFF) << 16)
                                        | ((payload[6] & 0xFF) << 8)
                                        | (payload[7] & 0xFF);
                                if (declared == payload.length - 8) {
                                    start = 8;
                                }
                            }
                            String line = new String(payload, start, payload.length - start, StandardCharsets.UTF_8);
                            output.append(line);
                        }

                        @Override
                        public void onComplete() {
                            done.countDown();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.debug("Log stream error for {}: {}", containerId, throwable.getMessage());
                            done.countDown();
                        }
                    });
            if (!done.await(8, TimeUnit.SECONDS)) {
                log.debug("Timed out waiting for logs from {}", containerId);
            }
        } catch (Exception e) {
            log.debug("Failed to read logs for {}: {}", containerId, e.getMessage());
        }
        return output.toString();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static String firstName(String[] names) {
        if (names == null || names.length == 0) {
            return "";
        }
        String name = names[0];
        return name.startsWith("/") ? name.substring(1) : name;
    }

    /** Falls back to the "Up 5 minutes (healthy)" status text when inspect is unavailable. */
    private static String deriveHealth(String status) {
        String lower = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (lower.contains("(unhealthy)")) {
            return "unhealthy";
        }
        if (lower.contains("(healthy)")) {
            return "healthy";
        }
        if (lower.contains("(health: starting)") || lower.contains("(starting)")) {
            return "starting";
        }
        if (lower.startsWith("exited") || lower.startsWith("dead")) {
            return "exited";
        }
        return "none";
    }

    private static List<DockerModels.PortBinding> bindings(ContainerPort[] ports) {
        if (ports == null || ports.length == 0) {
            return List.of();
        }
        List<DockerModels.PortBinding> result = new ArrayList<>();
        for (ContainerPort port : ports) {
            if (port.getPrivatePort() == null) {
                continue;
            }
            Integer publicPort = port.getPublicPort();
            if (publicPort == null || publicPort == 0) {
                continue;
            }
            result.add(new DockerModels.PortBinding(
                    publicPort,
                    port.getPrivatePort(),
                    port.getIp() == null ? "" : port.getIp(),
                    port.getType() == null ? "tcp" : port.getType()
            ));
        }
        return result;
    }

    private static List<DockerModels.PortBinding> inspectedBindings(InspectContainerResponse inspect) {
        List<DockerModels.PortBinding> result = new ArrayList<>();
        try {
            Object networkSettings = call(inspect, "getNetworkSettings");
            Object portsNode = networkSettings == null ? null : call(networkSettings, "getPorts");
            if (!(portsNode instanceof Map<?, ?> ports)) {
                return result;
            }
            for (Map.Entry<?, ?> entry : ports.entrySet()) {
                int containerPort = containerPortOf(entry.getKey());
                Object value = entry.getValue();
                if (containerPort <= 0 || value == null) {
                    continue;
                }
                if (!(value instanceof List<?> bindings)) {
                    continue;
                }
                for (Object item : bindings) {
                    Integer hostPort = asInt(call(item, "getHostPortSpec"));
                    if (hostPort == null || hostPort <= 0) {
                        continue;
                    }
                    String hostIp = asString(call(item, "getHostIpSpec"));
                    result.add(new DockerModels.PortBinding(hostPort, containerPort,
                            hostIp == null ? "" : hostIp, "tcp"));
                }
            }
        } catch (Exception e) {
            log.debug("Could not read inspect port bindings: {}", e.getMessage());
        }
        return result;
    }

    private static int containerPortOf(Object exposedPort) {
        if (exposedPort == null) {
            return -1;
        }
        Integer port = asInt(call(exposedPort, "getPort"));
        return port == null ? -1 : port;
    }

    private static String label(Map<?, ?> labels, String key) {
        Object value = labels.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> portStrings(List<DockerModels.PortBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
                .map(binding -> (binding.hostIp() == null || binding.hostIp().isBlank()
                        ? "0.0.0.0" : binding.hostIp())
                        + ":" + binding.hostPort() + "->" + binding.containerPort())
                .toList();
    }

    /**
     * docker-java exposes some inspect fields only in newer minor versions. Reading
     * them reflectively keeps the engine compatible across 3.4.x patch releases.
     */
    private static Object call(Object target, String getter) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return String.valueOf(value);
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
