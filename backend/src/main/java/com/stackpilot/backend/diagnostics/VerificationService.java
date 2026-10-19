package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.project.ProjectService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VerificationService {

    private final DiagnosticEngine engine;
    private final ProjectService projects;

    public VerificationService(DiagnosticEngine engine, ProjectService projects) {
        this.engine = engine;
        this.projects = projects;
    }

    public DiagnosticModels.VerificationResult verify(String runId, String findingId) {
        DiagnosticModels.Run original = engine.get(runId);
        DiagnosticModels.Finding finding = engine.finding(runId, findingId);
        String projectId = original.projectId();
        if (projectId != null) {
            projects.reindex(projectId);
        }
        DiagnosticModels.Run next = engine.run(projectId);
        List<DiagnosticModels.Finding> remaining = next.findings().stream()
                .filter(item -> item.ruleId().equals(finding.ruleId()))
                .toList();
        boolean resolved = remaining.isEmpty();
        String summary = resolved
                ? "The original issue (" + finding.ruleId() + ") is no longer detected after re-indexing configs and re-reading container logs."
                : "The same rule still fires. Remaining evidence: " + remaining.getFirst().summary();
        return new DiagnosticModels.VerificationResult(next.id(), findingId, resolved, summary, remaining);
    }
}
