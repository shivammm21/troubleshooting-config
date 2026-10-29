package com.stackpilot.backend.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.stackpilot.backend.project.model.ProjectModels;
import com.stackpilot.backend.store.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Feature 4 – Documentation-Aware Diagnostics (indexing half).
 *
 * <p>Walks a project and extracts everything the diagnostic rules need to reason
 * about the <em>intended</em> environment:
 * <ul>
 *   <li>Compose topology – services, published ports, environment, dependencies</li>
 *   <li>Configuration bindings – Redis/Kafka/database hosts, {@code server.port}</li>
 *   <li>{@code ${VAR}} placeholders plus which variables are actually defined</li>
 *   <li>Documentation facts – README/docs mentions of ports, service names, env vars
 *       and commands, with file and line numbers for evidence</li>
 *   <li>Dockerfile {@code EXPOSE} declarations</li>
 * </ul>
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private static final long MAX_FILE_BYTES = 512 * 1024;
    private static final int MAX_FILES = 400;
    private static final int MAX_DOC_FACTS = 400;

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "build", "target", ".gradle", "out", "dist", ".idea",
            ".next", "vendor", "__pycache__", ".venv", "venv"
    );

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\$\\{([A-Za-z0-9_.\\-]+)(?::([^}]*))?}");

    private static final Pattern LOCALHOST_PORT = Pattern.compile(
            "(?:localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0):(\\d{2,5})");

    private static final Pattern PORT_MENTION = Pattern.compile(
            "\\bport\\b[^\\n\\d]{0,12}(\\d{2,5})", Pattern.CASE_INSENSITIVE);

    private static final Pattern EXPOSE = Pattern.compile(
            "^\\s*EXPOSE\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ENV_VAR_TOKEN = Pattern.compile("\\b([A-Z][A-Z0-9_]{2,})\\b");

    private static final Pattern SERVICE_TOKEN = Pattern.compile("`([a-z][a-z0-9_-]{2,24})`");

    private static final Pattern COMPOSE_COMMAND = Pattern.compile(
            "(docker\\s+compose\\s+(?:up|run)[^`\\n]{0,60})", Pattern.CASE_INSENSITIVE);

    private static final Set<String> ENV_VAR_HINTS = Set.of(
            "SPRING", "REDIS", "KAFKA", "POSTGRES", "MYSQL", "MONGO", "DATABASE",
            "RABBIT", "ELASTIC", "JAVA_OPTS", "SERVER_PORT", "APP_ENV", "API_KEY"
    );

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final Map<String, ProjectModels.Snapshot> snapshots = new ConcurrentHashMap<>();
    private final StateStore store;

    @Autowired
    public ProjectService(StateStore store) {
        this.store = store;
    }

    /** Convenience constructor for unit tests that do not need persistence. */
    ProjectService() {
        this.store = null;
    }

    public ProjectModels.Snapshot attach(String rawPath) {
        return index(rawPath, UUID.randomUUID().toString());
    }

    public ProjectModels.Snapshot get(String id) {
        ProjectModels.Snapshot cached = snapshots.get(id);
        if (cached != null) {
            return cached;
        }
        if (store != null) {
            ProjectModels.ProjectRef ref = store.project(id).orElse(null);
            if (ref != null) {
                return index(ref.rootPath(), id);
            }
        }
        throw new NoSuchElementException("Unknown project: " + id);
    }

    public ProjectModels.Snapshot reindex(String id) {
        ProjectModels.Snapshot existing = get(id);
        return index(existing.rootPath(), id);
    }

    /** Attached projects, in-memory plus the ones restored from disk. */
    public List<ProjectModels.ProjectRef> list() {
        Map<String, ProjectModels.ProjectRef> refs = new LinkedHashMap<>();
        for (ProjectModels.Snapshot snapshot : snapshots.values()) {
            refs.put(snapshot.id(), new ProjectModels.ProjectRef(snapshot.id(), snapshot.rootPath()));
        }
        if (store != null) {
            for (ProjectModels.ProjectRef ref : store.projects()) {
                refs.putIfAbsent(ref.id(), ref);
            }
        }
        return List.copyOf(refs.values());
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
                    .limit(MAX_FILES)
                    .forEach(path -> indexFile(root, path, files));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read project: " + e.getMessage());
        }

        Extraction extraction = new Extraction();
        for (ProjectModels.IndexedFile file : files) {
            switch (file.kind()) {
                case "yaml" -> extractYaml(file, extraction);
                case "env" -> extractEnv(file, extraction);
                case "dockerfile" -> extractDockerfile(file, extraction);
                case "markdown" -> extractMarkdown(file, extraction);
                default -> {
                    // text files do not contribute facts
                }
            }
        }

        List<String> notes = new ArrayList<>(extraction.notes);
        if (extraction.services.isEmpty()) {
            notes.add("No docker-compose services were found in this project.");
        }
        if (extraction.docFacts.isEmpty()) {
            notes.add("No README or documentation facts were found, so diagnostics rely on configuration only.");
        }

        ProjectModels.Snapshot snapshot = new ProjectModels.Snapshot(
                id,
                root.toString(),
                List.copyOf(files),
                extraction.serviceNames(),
                List.copyOf(extraction.redisHosts),
                List.copyOf(extraction.kafkaBootstraps),
                List.copyOf(extraction.databaseHosts),
                List.copyOf(extraction.appPorts),
                List.copyOf(extraction.placeholders),
                List.copyOf(extraction.envKeys),
                List.copyOf(extraction.services),
                extraction.docFactList(),
                List.copyOf(new LinkedHashSet<>(notes))
        );
        snapshots.put(snapshot.id(), snapshot);
        if (store != null) {
            store.putProject(new ProjectModels.ProjectRef(id, root.toString()));
        }
        log.info("Indexed project {} at {} ({} files, {} services, {} doc facts)",
                id, root, snapshot.files().size(), snapshot.services().size(), snapshot.docFacts().size());
        return snapshot;
    }

    // ------------------------------------------------------------------
    // file discovery
    // ------------------------------------------------------------------

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

    private boolean isIndexable(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("readme.md")
                || name.startsWith("readme")
                || name.endsWith(".md")
                || name.equals("dockerfile")
                || name.startsWith("dockerfile.")
                || name.endsWith(".dockerfile")
                || name.startsWith("docker-compose")
                || name.equals("compose.yaml")
                || name.equals("compose.yml")
                || name.startsWith("application")
                || name.equals(".env")
                || name.startsWith(".env.")
                || name.endsWith(".env");
    }

    private boolean isSkipped(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (SKIP_DIRS.contains(part.toString().toLowerCase(Locale.ROOT))) {
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
        if (name.endsWith("dockerfile") || name.endsWith(".dockerfile")) {
            return "dockerfile";
        }
        return "text";
    }

    // ------------------------------------------------------------------
    // YAML (compose + spring config)
    // ------------------------------------------------------------------

    private void extractYaml(ProjectModels.IndexedFile file, Extraction extraction) {
        JsonNode root;
        try {
            root = yaml.readTree(file.content());
        } catch (Exception e) {
            extraction.notes.add("Could not parse " + file.relativePath() + ": " + e.getMessage());
            return;
        }
        if (root == null) {
            return;
        }
        if (looksLikeCompose(root)) {
            readCompose(file, root, extraction);
        }
        walkJson(file.relativePath(), "", root, extraction);
    }

    private static boolean looksLikeCompose(JsonNode root) {
        if (!root.has("services") || !root.get("services").isObject()) {
            return false;
        }
        // Spring configuration never defines a top-level `services` map with `image` children.
        Iterator<Map.Entry<String, JsonNode>> fields = root.get("services").fields();
        while (fields.hasNext()) {
            JsonNode service = fields.next().getValue();
            if (service != null && (service.has("image") || service.has("build") || service.has("ports"))) {
                return true;
            }
        }
        return false;
    }

    private void readCompose(ProjectModels.IndexedFile file, JsonNode root, Extraction extraction) {
        JsonNode servicesNode = root.get("services");
        Iterator<Map.Entry<String, JsonNode>> fields = servicesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            JsonNode service = entry.getValue();
            if (service == null || !service.isObject()) {
                continue;
            }

            List<ProjectModels.ServicePort> ports = new ArrayList<>();
            JsonNode portsNode = service.get("ports");
            if (portsNode != null && portsNode.isArray()) {
                for (JsonNode portNode : portsNode) {
                    ProjectModels.ServicePort parsed = parseServicePort(portNode);
                    if (parsed != null) {
                        ports.add(parsed);
                    }
                }
            }

            Map<String, String> environment = new LinkedHashMap<>();
            JsonNode envNode = service.get("environment");
            if (envNode != null) {
                if (envNode.isObject()) {
                    envNode.fields().forEachRemaining(e -> environment.put(
                            e.getKey(),
                            e.getValue() == null || e.getValue().isNull() ? "" : e.getValue().asText()));
                } else if (envNode.isArray()) {
                    for (JsonNode item : envNode) {
                        String text = item.asText();
                        int eq = text.indexOf('=');
                        if (eq > 0) {
                            environment.put(text.substring(0, eq).trim(), text.substring(eq + 1).trim());
                        } else if (!text.isBlank()) {
                            environment.put(text.trim(), "");
                        }
                    }
                }
            }
            environment.keySet().forEach(extraction.envKeys::add);

            List<String> dependsOn = new ArrayList<>();
            JsonNode dependsNode = service.get("depends_on");
            if (dependsNode != null) {
                if (dependsNode.isArray()) {
                    dependsNode.forEach(node -> dependsOn.add(node.asText()));
                } else if (dependsNode.isObject()) {
                    dependsNode.fieldNames().forEachRemaining(dependsOn::add);
                }
            }

            List<String> networks = new ArrayList<>();
            JsonNode networksNode = service.get("networks");
            if (networksNode != null) {
                if (networksNode.isArray()) {
                    networksNode.forEach(node -> networks.add(node.asText()));
                } else if (networksNode.isObject()) {
                    networksNode.fieldNames().forEachRemaining(networks::add);
                }
            }

            extraction.services.add(new ProjectModels.ComposeService(
                    name,
                    service.path("image").asText(""),
                    List.copyOf(ports),
                    Map.copyOf(environment),
                    List.copyOf(dependsOn),
                    List.copyOf(networks),
                    service.has("healthcheck"),
                    file.relativePath()
            ));
        }
    }

    private static ProjectModels.ServicePort parseServicePort(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            int target = node.path("target").asInt(0);
            int published = node.path("published").asInt(0);
            if (target <= 0) {
                return null;
            }
            String protocol = node.path("protocol").asText("tcp");
            return new ProjectModels.ServicePort(published, target, protocol);
        }
        String text = node.asText("").trim();
        if (text.isEmpty() || text.contains("${")) {
            return null;
        }
        // Forms: "8080:80", "127.0.0.1:8080:80", "80", "8080:80/udp"
        String protocol = "tcp";
        int slash = text.indexOf('/');
        if (slash > 0) {
            protocol = text.substring(slash + 1);
            text = text.substring(0, slash);
        }
        String[] parts = text.split(":");
        try {
            if (parts.length == 1) {
                // A bare container port: Docker picks a random host port, so no
                // host binding exists and this cannot conflict with anything.
                int port = Integer.parseInt(parts[0].trim());
                return new ProjectModels.ServicePort(0, port, protocol);
            }
            int containerPort = Integer.parseInt(parts[parts.length - 1].trim());
            int hostPort = Integer.parseInt(parts[parts.length - 2].trim());
            return new ProjectModels.ServicePort(hostPort, containerPort, protocol);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void walkJson(String file, String path, JsonNode node, Extraction extraction) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String child = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isValueNode()) {
                    classifyBinding(file, child, value.asText(), extraction);
                    collectPlaceholders(file, child, value.asText(), extraction);
                }
                walkJson(file, child, value, extraction);
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                walkJson(file, path, child, extraction);
            }
        }
    }

    // ------------------------------------------------------------------
    // .env / .properties
    // ------------------------------------------------------------------

    private void extractEnv(ProjectModels.IndexedFile file, Extraction extraction) {
        for (String line : file.content().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            String withoutExport = trimmed.startsWith("export ") ? trimmed.substring(7).trim() : trimmed;
            int eq = withoutExport.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = withoutExport.substring(0, eq).trim();
            String value = withoutExport.substring(eq + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            extraction.envKeys.add(key);
            classifyBinding(file.relativePath(), key, value, extraction);
            collectPlaceholders(file.relativePath(), key, value, extraction);
        }
    }

    // ------------------------------------------------------------------
    // Dockerfile
    // ------------------------------------------------------------------

    private void extractDockerfile(ProjectModels.IndexedFile file, Extraction extraction) {
        String[] lines = file.content().split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = EXPOSE.matcher(lines[i]);
            if (!matcher.find()) {
                continue;
            }
            for (String token : matcher.group(1).trim().split("\\s+")) {
                String port = token.contains("/") ? token.substring(0, token.indexOf('/')) : token;
                extraction.addDocFact(new ProjectModels.DocFact(
                        file.relativePath(), i + 1, "expose", port, lines[i].trim()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Markdown / README
    // ------------------------------------------------------------------

    private void extractMarkdown(ProjectModels.IndexedFile file, Extraction extraction) {
        String[] lines = file.content().split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int lineNumber = i + 1;

            Matcher localhost = LOCALHOST_PORT.matcher(line);
            while (localhost.find()) {
                extraction.addDocFact(new ProjectModels.DocFact(
                        file.relativePath(), lineNumber, "localhost-port", localhost.group(1), trimmed));
            }

            Matcher portMention = PORT_MENTION.matcher(line);
            while (portMention.find()) {
                extraction.addDocFact(new ProjectModels.DocFact(
                        file.relativePath(), lineNumber, "port-mention", portMention.group(1), trimmed));
            }

            Matcher envToken = ENV_VAR_TOKEN.matcher(line);
            while (envToken.find()) {
                String token = envToken.group(1);
                if (isLikelyEnvVar(token)) {
                    extraction.addDocFact(new ProjectModels.DocFact(
                            file.relativePath(), lineNumber, "env-var", token, trimmed));
                }
            }

            Matcher serviceToken = SERVICE_TOKEN.matcher(line);
            while (serviceToken.find()) {
                extraction.addDocFact(new ProjectModels.DocFact(
                        file.relativePath(), lineNumber, "service-mention", serviceToken.group(1), trimmed));
            }

            Matcher command = COMPOSE_COMMAND.matcher(line);
            while (command.find()) {
                extraction.addDocFact(new ProjectModels.DocFact(
                        file.relativePath(), lineNumber, "command", command.group(1).trim(), trimmed));
            }
        }
    }

    private static boolean isLikelyEnvVar(String token) {
        if (!token.contains("_")) {
            return false;
        }
        for (String hint : ENV_VAR_HINTS) {
            if (token.startsWith(hint) || token.endsWith(hint) || token.contains("_" + hint + "_")) {
                return true;
            }
        }
        return token.endsWith("_HOST") || token.endsWith("_PORT") || token.endsWith("_URL")
                || token.endsWith("_USERNAME") || token.endsWith("_PASSWORD")
                || token.endsWith("_SECRET") || token.endsWith("_TOKEN");
    }

    // ------------------------------------------------------------------
    // configuration bindings
    // ------------------------------------------------------------------

    private void classifyBinding(String file, String key, String value, Extraction extraction) {
        if (value == null || value.isBlank()) {
            return;
        }
        String k = key.toLowerCase(Locale.ROOT);

        if (k.endsWith("redis.host") || k.endsWith("redis.hostname")
                || k.endsWith("spring_data_redis_host") || k.endsWith("spring_redis_host")
                || k.endsWith("redis_host")) {
            extraction.redisHosts.add(new ProjectModels.HostBinding(file, key, value));
        } else if (k.contains("bootstrap-servers") || k.contains("bootstrap_servers")
                || k.endsWith("kafka.bootstrap.servers")
                || k.contains("spring_kafka_bootstrap_servers")) {
            extraction.kafkaBootstraps.add(new ProjectModels.HostBinding(file, key, value));
        } else if (isDatabaseKey(k, value)) {
            extraction.databaseHosts.add(new ProjectModels.HostBinding(file, key, value));
        }

        if (k.endsWith("server.port") || k.equals("server_port") || k.equals("port")
                && file.toLowerCase(Locale.ROOT).contains("application")) {
            extraction.appPorts.add(new ProjectModels.HostBinding(file, key, value));
        }
    }

    private static boolean isDatabaseKey(String key, String value) {
        boolean family = key.contains("postgres") || key.contains("mysql") || key.contains("mariadb")
                || key.contains("mongodb") || key.contains("mongo") || key.contains("rabbitmq")
                || key.contains("amqp") || key.contains("elasticsearch")
                || key.contains("datasource") || key.contains("jdbc");
        if (!family) {
            return false;
        }
        boolean hostish = key.endsWith(".host") || key.endsWith(".hostname") || key.endsWith(".url")
                || key.endsWith("_host") || key.endsWith("_url") || key.endsWith("_hostname")
                || key.contains("server-list") || key.contains("server_list")
                || key.contains("addresses");
        return hostish && value != null && !value.isBlank();
    }

    private void collectPlaceholders(String file, String key, String value, Extraction extraction) {
        if (value == null || !value.contains("${")) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            String variable = matcher.group(1);
            String fallback = matcher.group(2);
            extraction.placeholders.add(new ProjectModels.EnvPlaceholder(
                    file, key, variable, fallback));
        }
    }

    // ------------------------------------------------------------------
    // extraction accumulator
    // ------------------------------------------------------------------

    private static final class Extraction {
        private final List<ProjectModels.ComposeService> services = new ArrayList<>();
        private final List<ProjectModels.HostBinding> redisHosts = new ArrayList<>();
        private final List<ProjectModels.HostBinding> kafkaBootstraps = new ArrayList<>();
        private final List<ProjectModels.HostBinding> databaseHosts = new ArrayList<>();
        private final List<ProjectModels.HostBinding> appPorts = new ArrayList<>();
        private final List<ProjectModels.EnvPlaceholder> placeholders = new ArrayList<>();
        private final Set<String> envKeys = new LinkedHashSet<>();
        private final List<ProjectModels.DocFact> docFacts = new ArrayList<>();
        private final Set<String> docFactKeys = new LinkedHashSet<>();
        private final List<String> notes = new ArrayList<>();

        private void addDocFact(ProjectModels.DocFact fact) {
            if (docFacts.size() >= MAX_DOC_FACTS) {
                return;
            }
            String key = fact.source() + "|" + fact.line() + "|" + fact.kind() + "|" + fact.value();
            if (docFactKeys.add(key)) {
                docFacts.add(fact);
            }
        }

        private List<String> serviceNames() {
            Set<String> names = new LinkedHashSet<>();
            services.forEach(service -> names.add(service.name()));
            return List.copyOf(names);
        }

        private List<ProjectModels.DocFact> docFactList() {
            return List.copyOf(docFacts);
        }
    }
}
