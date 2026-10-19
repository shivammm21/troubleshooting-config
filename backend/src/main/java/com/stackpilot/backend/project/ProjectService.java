package com.stackpilot.backend.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.stackpilot.backend.project.model.ProjectModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final long MAX_FILE_BYTES = 512 * 1024;
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "build", "target", ".gradle", "out", "dist", ".idea"
    );

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final Map<String, ProjectModels.Snapshot> projects = new ConcurrentHashMap<>();

    public ProjectModels.Snapshot attach(String rawPath) {
        return index(rawPath, UUID.randomUUID().toString());
    }

    public ProjectModels.Snapshot get(String id) {
        ProjectModels.Snapshot snapshot = projects.get(id);
        if (snapshot == null) {
            throw new NoSuchElementException("Unknown project: " + id);
        }
        return snapshot;
    }

    public ProjectModels.Snapshot reindex(String id) {
        ProjectModels.Snapshot existing = get(id);
        return index(existing.rootPath(), id);
    }

    private ProjectModels.Snapshot index(String rawPath, String id) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Project path is required");
        }
        Path root = Path.of(rawPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        List<ProjectModels.IndexedFile> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, 8)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !isSkipped(root, path))
                    .filter(this::isIndexable)
                    .forEach(path -> indexFile(root, path, files));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read project: " + e.getMessage());
        }

        List<String> services = new ArrayList<>();
        List<ProjectModels.HostBinding> redisHosts = new ArrayList<>();
        List<ProjectModels.HostBinding> kafkaBootstraps = new ArrayList<>();
        for (ProjectModels.IndexedFile file : files) {
            extractFacts(file, services, redisHosts, kafkaBootstraps);
        }

        ProjectModels.Snapshot snapshot = new ProjectModels.Snapshot(
                id,
                root.toString(),
                files,
                services.stream().distinct().toList(),
                redisHosts,
                kafkaBootstraps
        );
        projects.put(snapshot.id(), snapshot);
        log.info("Indexed project {} with {} files at {}", snapshot.id(), files.size(), root);
        return snapshot;
    }

    private void indexFile(Path root, Path path, List<ProjectModels.IndexedFile> files) {
        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                return;
            }
            String relative = root.relativize(path).toString().replace('\\', '/');
            String content = Files.readString(path, StandardCharsets.UTF_8);
            files.add(new ProjectModels.IndexedFile(relative, kind(relative), content));
        } catch (Exception e) {
            log.debug("Skipping unreadable file {}: {}", path, e.getMessage());
        }
    }

    private void extractFacts(
            ProjectModels.IndexedFile file,
            List<String> services,
            List<ProjectModels.HostBinding> redisHosts,
            List<ProjectModels.HostBinding> kafkaBootstraps
    ) {
        if (!"yaml".equals(file.kind()) && !"env".equals(file.kind())) {
            return;
        }
        if ("env".equals(file.kind())) {
            for (String line : file.content().split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                classifyBinding(file.relativePath(), key, value, redisHosts, kafkaBootstraps);
            }
            return;
        }
        try {
            JsonNode root = yaml.readTree(file.content());
            if (root != null && root.has("services") && root.get("services").isObject()) {
                Iterator<String> names = root.get("services").fieldNames();
                names.forEachRemaining(services::add);
            }
            walkJson(file.relativePath(), "", root, redisHosts, kafkaBootstraps);
        } catch (Exception e) {
            log.debug("YAML parse skipped for {}: {}", file.relativePath(), e.getMessage());
        }
    }

    private void walkJson(
            String file,
            String path,
            JsonNode node,
            List<ProjectModels.HostBinding> redisHosts,
            List<ProjectModels.HostBinding> kafkaBootstraps
    ) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String child = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isValueNode()) {
                    classifyBinding(file, child, value.asText(), redisHosts, kafkaBootstraps);
                }
                walkJson(file, child, value, redisHosts, kafkaBootstraps);
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                walkJson(file, path, child, redisHosts, kafkaBootstraps);
            }
        }
    }

    private static void classifyBinding(
            String file,
            String key,
            String value,
            List<ProjectModels.HostBinding> redisHosts,
            List<ProjectModels.HostBinding> kafkaBootstraps
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.endsWith("redis.host")
                || k.endsWith("redis.hostname")
                || k.endsWith("spring_data_redis_host")
                || k.endsWith("spring_redis_host")
                || k.endsWith("redis_host")) {
            redisHosts.add(new ProjectModels.HostBinding(file, key, value));
        }
        if (k.contains("bootstrap-servers")
                || k.contains("bootstrap_servers")
                || k.endsWith("kafka.bootstrap.servers")
                || k.contains("spring_kafka_bootstrap_servers")) {
            kafkaBootstraps.add(new ProjectModels.HostBinding(file, key, value));
        }
    }

    private boolean isIndexable(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("readme.md")
                || name.equals("dockerfile")
                || name.startsWith("docker-compose")
                || name.equals("compose.yaml")
                || name.equals("compose.yml")
                || name.startsWith("application") && (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties"))
                || name.equals(".env")
                || name.equals(".env.example")
                || name.endsWith(".env");
    }

    private boolean isSkipped(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (SKIP_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String kind(String relative) {
        String name = relative.toLowerCase(Locale.ROOT);
        if (name.endsWith(".md")) {
            return "markdown";
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            return "yaml";
        }
        if (name.endsWith(".properties") || name.endsWith(".env") || name.contains(".env.")) {
            return "env";
        }
        return "text";
    }
}
