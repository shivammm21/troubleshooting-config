package com.stackpilot.backend.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
public class DockerJavaEngine implements DockerEngine {

    private static final Logger log = LoggerFactory.getLogger(DockerJavaEngine.class);

    private volatile DockerClient client;

    private synchronized DockerClient client() {
        if (client == null) {
            var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            var http = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(20)
                    .connectionTimeout(Duration.ofSeconds(3))
                    .responseTimeout(Duration.ofSeconds(12))
                    .build();
            client = DockerClientImpl.getInstance(config, http);
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
                result.add(new DockerModels.ContainerSummary(
                        container.getId(),
                        firstName(container.getNames()),
                        container.getImage(),
                        container.getState(),
                        container.getStatus(),
                        ports(container.getPorts())
                ));
            }
            return result;
        } catch (Exception e) {
            log.debug("Failed to list containers: {}", e.getMessage());
            return List.of();
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
                            // Skip the 8-byte multiplexing header if still present:
                            // byte[0] is stream type (0=stdin,1=stdout,2=stderr), bytes 1-3 are
                            // padding zeros, bytes 4-7 are big-endian payload length.
                            if (payload.length > 8
                                    && (payload[0] == 0 || payload[0] == 1 || payload[0] == 2)
                                    && payload[1] == 0 && payload[2] == 0 && payload[3] == 0) {
                                int declared = ((payload[4] & 0xFF) << 24)
                                        | ((payload[5] & 0xFF) << 16)
                                        | ((payload[6] & 0xFF) << 8)
                                        | (payload[7] & 0xFF);
                                // Only skip the header when declared length is consistent.
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

    private static String firstName(String[] names) {
        if (names == null || names.length == 0) {
            return "";
        }
        String name = names[0];
        return name.startsWith("/") ? name.substring(1) : name;
    }

    private static List<String> ports(ContainerPort[] ports) {
        if (ports == null || ports.length == 0) {
            return List.of();
        }
        return Arrays.stream(ports)
                .map(port -> {
                    String publicPort = port.getPublicPort() == null ? "" : String.valueOf(port.getPublicPort());
                    String privatePort = port.getPrivatePort() == null ? "" : String.valueOf(port.getPrivatePort());
                    String ip = port.getIp() == null ? "" : port.getIp();
                    if (publicPort.isBlank()) {
                        return privatePort;
                    }
                    return (ip.isBlank() ? "0.0.0.0" : ip) + ":" + publicPort + "->" + privatePort;
                })
                .toList();
    }
}
