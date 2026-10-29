package com.stackpilot.backend.logs.model;

import java.util.List;

/**
 * Result of analysing a container's log tail.
 */
public final class LogModels {

    private LogModels() {
    }

    /**
     * A grouped failure signature.
     *
     * @param signature normalised text used as the grouping key
     * @param category  CONNECTION | DNS | AUTH | PORT | OOM | CONFIG | MIGRATION | TIMEOUT | CRASH | DEPENDENCY | OTHER
     * @param severity  ERROR | WARN
     * @param count     how many log lines matched
     * @param sample    the first matching raw line (truncated)
     * @param line      the 1-based line number of the first match
     */
    public record Signature(
            String signature,
            String category,
            String severity,
            int count,
            String sample,
            int line
    ) {
    }

    public record Summary(
            int totalLines,
            int errorLines,
            int warnLines,
            int infoLines,
            int stackTraceLines,
            List<Signature> signatures,
            List<String> exceptionTypes
    ) {
        public static Summary empty() {
            return new Summary(0, 0, 0, 0, 0, List.of(), List.of());
        }

        public boolean hasErrors() {
            return errorLines > 0 || signatures.stream().anyMatch(s -> "ERROR".equals(s.severity()));
        }

        public List<String> categories() {
            return signatures.stream().map(Signature::category).distinct().toList();
        }
    }

    public record ContainerLogs(
            String containerId,
            String containerName,
            int lineCount,
            String raw,
            Summary summary
    ) {
    }
}
