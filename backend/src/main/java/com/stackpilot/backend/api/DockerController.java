package com.stackpilot.backend.api;

import com.stackpilot.backend.docker.DockerEngine;
import com.stackpilot.backend.docker.model.DockerModels;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class DockerController {

    private final DockerEngine docker;

    public DockerController(DockerEngine docker) {
        this.docker = docker;
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
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Container id is required");
        }
        if (lines < 1 || lines > 2000) {
            throw new IllegalArgumentException("lines must be between 1 and 2000");
        }
        boolean known = !docker.status().connected() || docker.listContainers().stream().anyMatch(c ->
                c.id().equalsIgnoreCase(id) ||
                c.id().startsWith(id) ||
                id.startsWith(c.id()) ||
                c.name().equalsIgnoreCase(id) ||
                c.name().equalsIgnoreCase(id.replaceFirst("^/", "")) ||
                id.equalsIgnoreCase(c.name())
        );
        if (!known) {
            try {
                String directLogs = docker.tailLogs(id, lines);
                if (directLogs != null && !directLogs.isBlank()) {
                    return ResponseEntity.ok(Map.of("containerId", id, "logs", sanitise(directLogs)));
                }
            } catch (Exception ignored) {
            }
            throw new NoSuchElementException("Container not found: " + id);
        }
        return ResponseEntity.ok(Map.of(
                "containerId", id,
                "logs", sanitise(docker.tailLogs(id, lines))
        ));
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
