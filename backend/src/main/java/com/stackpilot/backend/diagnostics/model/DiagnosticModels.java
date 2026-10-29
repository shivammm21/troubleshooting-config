package com.stackpilot.backend.diagnostics.model;

import java.time.Instant;
import java.util.List;

public final class DiagnosticModels {

    private DiagnosticModels() {
    }

    public record Evidence(String source, String location, String excerpt) {
    }

    /**
     * A single file mutation.
     *
     * @param mode REPLACE (default), APPEND_LINE or CREATE_FILE
     */
    public record FilePatch(String path, String originalSnippet, String replacementSnippet, String mode) {

        public FilePatch(String path, String originalSnippet, String replacementSnippet) {
            this(path, originalSnippet, replacementSnippet, "REPLACE");
        }

        public static FilePatch append(String path, String line) {
            return new FilePatch(path, "", line, "APPEND_LINE");
        }

        public static FilePatch create(String path, String content) {
            return new FilePatch(path, "", content, "CREATE_FILE");
        }
    }

    /**
     * A non-file action that needs developer approval, e.g. restarting a container.
     *
     * @param kind RESTART_CONTAINER | START_CONTAINER
     */
    public record RepairAction(String kind, String target, String description) {
    }

    /**
     * @param risk           LOW | MEDIUM | HIGH
     * @param requiresRestart whether the change only takes effect after the affected
     *                        containers are restarted
     */
    public record RepairSuggestion(
            String description,
            List<FilePatch> patches,
            List<RepairAction> actions,
            String risk,
            boolean requiresRestart
    ) {

        public RepairSuggestion(String description, List<FilePatch> patches) {
            this(description, patches, List.of(), "LOW", false);
        }

        public static RepairSuggestion of(String description, List<FilePatch> patches, String risk, boolean restart) {
            return new RepairSuggestion(description, patches, List.of(), risk, restart);
        }

        public static RepairSuggestion action(String description, RepairAction action, String risk) {
            return new RepairSuggestion(description, List.of(), List.of(action), risk, false);
        }

        public boolean hasFileChanges() {
            return patches != null && !patches.isEmpty();
        }

        public boolean hasActions() {
            return actions != null && !actions.isEmpty();
        }

        public boolean isEmpty() {
            return !hasFileChanges() && !hasActions();
        }
    }

    /**
     * @param confidence 0-100, how strongly the evidence supports the conclusion
     * @param category   infra | configuration | documentation | runtime
     * @param rootCauseKey groups related findings so duplicates collapse
     * @param nextSteps  ordered manual steps when no automated patch exists
     */
    public record Finding(
            String id,
            String ruleId,
            String title,
            String severity,
            String summary,
            List<Evidence> evidence,
            RepairSuggestion repair,
            int confidence,
            String category,
            String rootCauseKey,
            List<String> nextSteps
    ) {

        public Finding(
                String id,
                String ruleId,
                String title,
                String severity,
                String summary,
                List<Evidence> evidence,
                RepairSuggestion repair
        ) {
            this(id, ruleId, title, severity, summary, evidence, repair, 70, "configuration", id, List.of());
        }

        public boolean fixable() {
            return repair != null && !repair.isEmpty();
        }

        public int severityRank() {
            return switch (severity == null ? "" : severity.toUpperCase()) {
                case "CRITICAL", "ERROR" -> 0;
                case "WARN", "WARNING" -> 1;
                default -> 2;
            };
        }
    }

    public record RunSummary(
            int total,
            int errors,
            int warnings,
            int repairable,
            List<String> services
    ) {
    }

    public record LogHealth(
            int containers,
            int totalLines,
            int errorLines,
            List<String> topSignatures
    ) {
        public static LogHealth empty() {
            return new LogHealth(0, 0, 0, List.of());
        }
    }

    public record Run(
            String id,
            Instant createdAt,
            String projectId,
            List<Finding> findings,
            RunSummary summary,
            LogHealth logHealth
    ) {

        public Run(String id, Instant createdAt, String projectId, List<Finding> findings) {
            this(id, createdAt, projectId, findings,
                    new RunSummary(findings == null ? 0 : findings.size(), 0, 0, 0, List.of()),
                    LogHealth.empty());
        }
    }

    public record RepairResult(
            String findingId,
            boolean applied,
            List<String> changedFiles,
            List<String> backups,
            List<String> restartedContainers,
            String message
    ) {
    }

    /**
     * @param status RESOLVED | CONFIG_FIXED_RUNTIME_PENDING | UNRESOLVED | NO_FIX
     */
    public record VerificationResult(
            String runId,
            String findingId,
            boolean resolved,
            String status,
            String summary,
            List<Finding> remaining,
            List<String> runtimeChecks,
            Instant checkedAt
    ) {
    }

    /** Compact entry for the history panel. */
    public record RunRef(
            String id,
            Instant createdAt,
            String projectId,
            int findings,
            int errors,
            int repairable
    ) {
    }
}
