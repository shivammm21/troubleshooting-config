package com.stackpilot.backend.api;

import com.stackpilot.backend.docker.DockerEngine;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.logs.LogAnalyzer;
import com.stackpilot.backend.logs.model.LogModels;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class DockerController {

    private static final int MAX_LOG_LINES = 2000;

    private final DockerEngine docker;
    private final LogAnalyzer analyzer;

    public DockerController(DockerEngine docker, LogAnalyzer analyzer) {
        this.docker = docker;
        this.analyzer = analyzer;
    }

    @GetMapping("/docker/status")
    public DockerModels.Status status() {
        return docker.status();
    }

    @GetMapping("/containers")
    public List<DockerModels.ContainerSummary> containers() {
        return docker.listContainers();
    }

    @GetMapping("/containers/{id}/logs")
    public ResponseEntity<Map<String, String>> logs(
            @PathVariable String id,
            @RequestParam(defaultValue = "200") int lines
    ) {
        int limit = clampLines(lines);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Container id is required");
        }
        return ResponseEntity.ok(Map.of(
                "containerId", id,
                "logs", sanitise(readLogs(id, limit))
        ));
    }

    /**
     * Feature 2 – Log Analysis and Error Detection.
     *
     * <p>Returns the raw tail plus the grouped failure signatures so the UI can
     * show "12 x connection refused" instead of a wall of text.
     */
    @GetMapping("/containers/{id}/log-analysis")
    public LogAnalysis analysis(
            @PathVariable String id,
            @RequestParam(defaultValue = "300") int lines
    ) {
        int limit = clampLines(lines);
        String target = resolveContainerId(id);
        String clean = sanitise(docker.tailLogs(target, limit));
        LogModels.Summary summary = analyzer.analyze(clean);
        String name = docker.listContainers().stream()
                .filter(c -> c.id().equals(target) || c.name().equalsIgnoreCase(target))
                .map(DockerModels.ContainerSummary::name)
                .findFirst()
                .orElse(target);
        return new LogAnalysis(target, name, limit, clean, summary);
    }

    /** Explicit, user-initiated container actions from the health panel. */
    @PostMapping("/containers/{id}/restart")
    public ActionResult restart(@PathVariable String id) {
        String target = resolveContainerId(id);
        boolean ok = docker.restart(target);
        return new ActionResult(target, ok, ok
                ? "Restart requested. Give the container a few seconds to become healthy."
                : "Docker refused the restart. Check `docker ps -a` and the engine logs.");
    }

    @PostMapping("/containers/{id}/start")
    public ActionResult start(@PathVariable String id) {
        String target = resolveContainerId(id);
        boolean ok = docker.start(target);
        return new ActionResult(target, ok, ok
                ? "Start requested."
                : "Docker refused to start the container. Read its log for the cause.");
    }

    /** Log tail for a container id, short id or name; throws 404 when unknown. */
    private String readLogs(String id, int lines) {
        return docker.tailLogs(resolveContainerId(id), lines);
    }

    private String resolveContainerId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Container id is required");
        }
        if (!docker.status().connected()) {
            // Offline: hand the reference straight to the engine, which will fail
            // with a clear error instead of pretending the container exists.
            return id;
        }
        String plain = id.replaceFirst("^/", "");
        return docker.listContainers().stream()
                .filter(c -> c.id().equalsIgnoreCase(plain)
                        || c.id().startsWith(plain)
                        || plain.startsWith(c.id())
                        || c.name().equalsIgnoreCase(plain)
                        || c.name().replaceFirst("^/", "").equalsIgnoreCase(plain))
                .map(DockerModels.ContainerSummary::id)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Container not found: " + id));
    }

    private static int clampLines(int lines) {
        if (lines < 1) {
            throw new IllegalArgumentException("lines must be at least 1");
        }
        return Math.min(lines, MAX_LOG_LINES);
    }

    /** Raw tail + analysed signatures for one container. */
    public record LogAnalysis(
            String containerId,
            String containerName,
            int requestedLines,
            String raw,
            LogModels.Summary summary
    ) {
        public LogAnalysis {
            // Keeps the record immutable even if the analyser returns null.
            raw = raw == null ? "" : raw;
            summary = summary == null ? LogModels.Summary.empty() : summary;
        }
    }

    public record ActionResult(String containerId, boolean ok, String message) {
    }

    /**
     * Removes residual Docker multiplexing header bytes that may slip through on
     * some Docker versions / transports. Each Docker log line can be prefixed with
     * an 8-byte binary frame header (stream-type + 3 zeros + 4-byte length). We
     * strip any such prefix from the start of every line.
     */
    private static String sanitise(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw == null ? "" : raw;
        }
        String[] lines = raw.split("\\R", -1);
        StringBuilder sb = new StringBuilder(raw.length());
        for (String line : lines) {
            sb.append(cleanLine(line)).append('\n');
        }
        // Remove trailing newline added by loop.
        if (!raw.endsWith("\n") && sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Strips an 8-byte Docker stream header from the start of a single line if
     * present, then removes any remaining leading non-printable characters.
     */
    private static String cleanLine(String line) {
        if (line == null || line.isEmpty()) {
            return line == null ? "" : line;
        }
        char first = line.charAt(0);
        // Docker multiplexed header: first byte is 0, 1, or 2 (stream type).
        if ((first == '\u0000' || first == '\u0001' || first == '\u0002') && line.length() > 8) {
            line = line.substring(8);
        }
        // Strip any remaining leading non-printable chars (< space, except tab).
        int start = 0;
        while (start < line.length()) {
            char c = line.charAt(start);
            if (c >= ' ' || c == '\t') {
                break;
            }
            start++;
        }
        return start > 0 ? line.substring(start) : line;
    }
}
