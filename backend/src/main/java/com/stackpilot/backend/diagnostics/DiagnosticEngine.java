package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.DockerEngine;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.health.HealthService;
import com.stackpilot.backend.health.model.HealthModels;
import com.stackpilot.backend.logs.LogAnalyzer;
import com.stackpilot.backend.logs.model.LogModels;
import com.stackpilot.backend.project.ProjectService;
import com.stackpilot.backend.project.model.ProjectModels;
import com.stackpilot.backend.store.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates one diagnostic pass:
 * <ol>
 *   <li>read the indexed project snapshot</li>
 *   <li>collect Docker state, container logs (raw + analysed) and TCP probes</li>
 *   <li>evaluate every rule in specificity order</li>
 *   <li>collapse duplicate root causes, rank by severity and confidence</li>
 *   <li>persist the run so history survives a restart</li>
 * </ol>
 */
@Service
public class DiagnosticEngine {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticEngine.class);

    private static final int LOG_TAIL_LINES = 200;
    private static final int MAX_CONTAINERS = 25;

    private final List<DiagnosticRule> rules;
    private final DockerEngine docker;
    private final ProjectService projects;
    private final LogAnalyzer logAnalyzer;
    private final HealthService health;
    private final StateStore store;

    public DiagnosticEngine(
            List<DiagnosticRule> rules,
            DockerEngine docker,
            ProjectService projects,
            LogAnalyzer logAnalyzer,
            HealthService health,
            StateStore store
    ) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(DiagnosticRule::order))
                .toList();
        this.docker = docker;
        this.projects = projects;
        this.logAnalyzer = logAnalyzer;
        this.health = health;
        this.store = store;
        log.info("Diagnostic rules registered: {}", this.rules.stream().map(DiagnosticRule::id).toList());
    }

    public DiagnosticModels.Run run(String projectId) {
        ProjectModels.Snapshot project = null;
        if (projectId != null && !projectId.isBlank()) {
            project = projects.get(projectId);
        }

        DockerModels.Status status = docker.status();
        List<DockerModels.ContainerSummary> containers = status.connected()
                ? docker.listContainers()
                : List.of();

        Map<String, String> logs = new LinkedHashMap<>();
        Map<String, LogModels.Summary> summaries = new LinkedHashMap<>();
        int analyzed = 0;
        for (DockerModels.ContainerSummary container : containers) {
            if (analyzed++ >= MAX_CONTAINERS) {
                break;
            }
            String raw = docker.tailLogs(container.id(), LOG_TAIL_LINES);
            if (raw == null) {
                raw = "";
            }
            logs.put(container.id(), raw);
            summaries.put(container.id(), logAnalyzer.analyze(raw));
        }

        List<HealthModels.Dependency> dependencies = status.connected()
                ? health.dependencies(containers)
                : List.of();

        Set<String> claims = new LinkedHashSet<>();
        DiagnosticContext context = new DiagnosticContext(
                project, status, containers, logs, summaries, dependencies, claims);

        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        for (DiagnosticRule rule : rules) {
            try {
                findings.addAll(rule.evaluate(context));
            } catch (Exception e) {
                log.warn("Diagnostic rule {} failed: {}", rule.id(), e.toString());
            }
        }

        List<DiagnosticModels.Finding> ranked = rank(dedupe(findings));

        int errors = 0;
        int warnings = 0;
        int repairable = 0;
        Set<String> services = new LinkedHashSet<>();
        for (DiagnosticModels.Finding finding : ranked) {
            if (finding.severityRank() == 0) {
                errors++;
            } else if (finding.severityRank() == 1) {
                warnings++;
            }
            if (finding.fixable()) {
                repairable++;
            }
            finding.evidence().stream()
                    .filter(evidence -> "DOCKER".equals(evidence.source()))
                    .findFirst()
                    .ifPresent(evidence -> services.add(evidence.location()));
        }

        int totalLines = summaries.values().stream().mapToInt(LogModels.Summary::totalLines).sum();
        int errorLines = summaries.values().stream().mapToInt(LogModels.Summary::errorLines).sum();
        List<String> topSignatures = summaries.values().stream()
                .flatMap(summary -> summary.signatures().stream())
                .filter(signature -> "ERROR".equals(signature.severity()))
                .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                .limit(3)
                .map(LogModels.Signature::signature)
                .toList();

        DiagnosticModels.Run run = new DiagnosticModels.Run(
                UUID.randomUUID().toString(),
                Instant.now(),
                project == null ? null : project.id(),
                ranked,
                new DiagnosticModels.RunSummary(ranked.size(), errors, warnings, repairable, List.copyOf(services)),
                new DiagnosticModels.LogHealth(containers.size(), totalLines, errorLines, topSignatures)
        );
        store.putRun(run);
        log.info("Diagnostic run {} produced {} finding(s) ({} errors, {} repairable)",
                run.id(), ranked.size(), errors, repairable);
        return run;
    }

    public DiagnosticModels.Run get(String id) {
        return store.run(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown diagnostic run: " + id));
    }

    public DiagnosticModels.Finding finding(String runId, String findingId) {
        return get(runId).findings().stream()
                .filter(finding -> finding.id().equals(findingId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown finding: " + findingId));
    }

    public List<DiagnosticModels.RunRef> history(int limit) {
        return store.runs(Math.max(1, limit)).stream()
                .map(run -> new DiagnosticModels.RunRef(
                        run.id(),
                        run.createdAt(),
                        run.projectId(),
                        run.findings().size(),
                        run.summary() == null ? 0 : run.summary().errors(),
                        run.summary() == null ? 0 : run.summary().repairable()))
                .toList();
    }

    /** Collapses findings that describe the same root cause. */
    private static List<DiagnosticModels.Finding> dedupe(List<DiagnosticModels.Finding> findings) {
        Map<String, DiagnosticModels.Finding> best = new LinkedHashMap<>();
        for (DiagnosticModels.Finding finding : findings) {
            String key = finding.rootCauseKey() == null || finding.rootCauseKey().isBlank()
                    ? finding.id()
                    : finding.rootCauseKey();
            DiagnosticModels.Finding existing = best.get(key);
            if (existing == null || better(finding, existing)) {
                best.put(key, finding);
            }
        }
        return new ArrayList<>(best.values());
    }

    private static boolean better(DiagnosticModels.Finding candidate, DiagnosticModels.Finding current) {
        if (candidate.severityRank() != current.severityRank()) {
            return candidate.severityRank() < current.severityRank();
        }
        if (candidate.confidence() != current.confidence()) {
            return candidate.confidence() > current.confidence();
        }
        return candidate.evidence().size() > current.evidence().size();
    }

    private static List<DiagnosticModels.Finding> rank(List<DiagnosticModels.Finding> findings) {
        List<DiagnosticModels.Finding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator
                .comparingInt(DiagnosticModels.Finding::severityRank)
                .thenComparing(Comparator.comparingInt(DiagnosticModels.Finding::confidence).reversed())
                .thenComparing(DiagnosticModels.Finding::title));
        return List.copyOf(sorted);
    }
}
