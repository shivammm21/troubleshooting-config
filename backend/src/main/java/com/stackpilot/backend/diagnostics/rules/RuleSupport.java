package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.project.model.ProjectModels;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Small helpers shared by the rule implementations. */
final class RuleSupport {

    private RuleSupport() {
    }

    static boolean isLoopback(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("localhost") || v.startsWith("127.0.0.1") || v.startsWith("0.0.0.0")
                || v.startsWith("host.docker.internal");
    }

    /** Extracts the host part of a bare host, host:port, URL or JDBC URL. */
    static String hostOf(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        int scheme = v.indexOf("://");
        if (scheme > 0) {
            v = v.substring(scheme + 3);
        }
        int at = v.indexOf('@');
        if (at >= 0) {
            v = v.substring(at + 1);
        }
        int slash = v.indexOf('/');
        if (slash >= 0) {
            v = v.substring(0, slash);
        }
        int semi = v.indexOf(';');
        if (semi >= 0) {
            v = v.substring(0, semi);
        }
        int comma = v.indexOf(',');
        if (comma >= 0) {
            v = v.substring(0, comma);
        }
        int colon = v.indexOf(':');
        if (colon >= 0) {
            v = v.substring(0, colon);
        }
        return v.trim();
    }

    static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() > limit ? trimmed.substring(0, limit) : trimmed;
    }

    static String excerpt(String logs, Pattern pattern) {
        if (logs == null || logs.isBlank()) {
            return "";
        }
        for (String line : logs.split("\\R")) {
            if (pattern.matcher(line).find()) {
                return truncate(line, 400);
            }
        }
        return truncate(logs, 400);
    }

    /** Finds the first line of a file content containing {@code needle}. */
    static String lineContaining(String content, String needle) {
        if (content == null || needle == null) {
            return "";
        }
        for (String line : content.split("\\R")) {
            if (line.contains(needle)) {
                return line.strip();
            }
        }
        return "";
    }

    static int countOccurrences(String content, String needle) {
        if (content == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    static String id() {
        return UUID.randomUUID().toString();
    }

    static DiagnosticModels.Evidence config(ProjectModels.HostBinding binding) {
        return new DiagnosticModels.Evidence("CONFIG", binding.file() + " → " + binding.key(), binding.value());
    }

    static DiagnosticModels.Evidence evidence(String source, String location, String excerpt) {
        return new DiagnosticModels.Evidence(source, location, truncate(excerpt, 400));
    }

    /** Picks the next free host port at or above {@code start}. */
    static int nextFreePort(int start, Iterable<Integer> taken) {
        List<Integer> used = new ArrayList<>();
        taken.forEach(used::add);
        int candidate = Math.max(1024, start);
        while (used.contains(candidate) && candidate < 65535) {
            candidate++;
        }
        if (candidate >= 65535) {
            // wrap around to a high ephemeral port
            candidate = 18080;
            while (used.contains(candidate) && candidate < 65535) {
                candidate++;
            }
        }
        return candidate;
    }
}
