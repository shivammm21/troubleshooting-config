package com.stackpilot.backend.diagnostics.model;

import java.time.Instant;
import java.util.List;

public final class DiagnosticModels {

    private DiagnosticModels() {
    }

    public record Evidence(String source, String location, String excerpt) {
    }

    public record FilePatch(String path, String originalSnippet, String replacementSnippet) {
    }

    public record RepairSuggestion(String description, List<FilePatch> patches) {
    }

    public record Finding(
            String id,
            String ruleId,
            String title,
            String severity,
            String summary,
            List<Evidence> evidence,
            RepairSuggestion repair
    ) {
    }

    public record Run(
            String id,
            Instant createdAt,
            String projectId,
            List<Finding> findings
    ) {
    }

    public record RepairResult(
            String findingId,
            boolean applied,
            List<String> changedFiles,
            String message
    ) {
    }

    public record VerificationResult(
            String runId,
            String findingId,
            boolean resolved,
            String summary,
            List<Finding> remaining
    ) {
    }
}
