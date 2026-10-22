package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.DockerEngine;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.project.ProjectService;
import com.stackpilot.backend.project.model.ProjectModels;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DiagnosticEngine {

    private final List<DiagnosticRule> rules;
    private final DockerEngine docker;
    private final ProjectService projects;
    private final Map<String, DiagnosticModels.Run> runs = new ConcurrentHashMap<>();

    public DiagnosticEngine(List<DiagnosticRule> rules, DockerEngine docker, ProjectService projects) {
        this.rules = rules;
        this.docker = docker;
        this.projects = projects;
    }

    public DiagnosticModels.Run run(String projectId) {
        ProjectModels.Snapshot project = null;
        if (projectId != null && !projectId.isBlank()) {
            project = projects.get(projectId);
        }
        DockerModels.Status status = docker.status();
        List<DockerModels.ContainerSummary> containers = status.connected() ? docker.listContainers() : List.of();
        // Build the logs map with TWO keys per container so that:
        //   - Old rules that look up by container.id() still find logs.
        //   - Evidence items show a human-readable "name (shortId)" label.
        // The readable label is inserted first so it is preferred when iterating;
        // the raw ID entry is a fallback lookup alias.
        Map<String, String> logs = new LinkedHashMap<>();
        for (DockerModels.ContainerSummary container : containers) {
            String shortId = container.id().length() > 12 ? container.id().substring(0, 12) : container.id();
            String readableKey = container.name().isBlank()
                    ? shortId
                    : container.name() + " (" + shortId + ")";
            String logContent = docker.tailLogs(container.id(), 150);
            logs.put(readableKey, logContent);   // human-readable key for evidence display
            logs.put(container.id(), logContent); // raw-ID alias so existing rules still resolve
        }
        DiagnosticContext context = new DiagnosticContext(project, status, containers, logs);
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        for (DiagnosticRule rule : rules) {
            findings.addAll(rule.evaluate(context));
        }
        DiagnosticModels.Run run = new DiagnosticModels.Run(
                UUID.randomUUID().toString(),
                Instant.now(),
                project == null ? null : project.id(),
                findings
        );
        runs.put(run.id(), run);
        return run;
    }

    public DiagnosticModels.Run get(String id) {
        DiagnosticModels.Run run = runs.get(id);
        if (run == null) {
            throw new NoSuchElementException("Unknown diagnostic run: " + id);
        }
        return run;
    }

    public DiagnosticModels.Finding finding(String runId, String findingId) {
        return get(runId).findings().stream()
                .filter(finding -> finding.id().equals(findingId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown finding: " + findingId));
    }
}
