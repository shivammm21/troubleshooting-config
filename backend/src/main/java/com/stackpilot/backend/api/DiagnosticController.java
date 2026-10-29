package com.stackpilot.backend.api;

import com.stackpilot.backend.api.dto.ApiRequests;
import com.stackpilot.backend.diagnostics.DiagnosticEngine;
import com.stackpilot.backend.diagnostics.RepairService;
import com.stackpilot.backend.diagnostics.ReportService;
import com.stackpilot.backend.diagnostics.VerificationService;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.store.StateStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Feature 2 (log-driven findings), 3 (root cause), 5 (approved fixes) and
 * 6 (post-fix verification) over HTTP.
 *
 * <p>Nothing is mutated by a read endpoint: a repair only happens through
 * {@code POST /repair-approval}, which the desktop client sends after the
 * developer confirmed the exact diff.
 */
@RestController
@RequestMapping("/api")
public class DiagnosticController {

    private final DiagnosticEngine engine;
    private final RepairService repairs;
    private final VerificationService verification;
    private final ReportService reports;
    private final StateStore store;

    public DiagnosticController(
            DiagnosticEngine engine,
            RepairService repairs,
            VerificationService verification,
            ReportService reports,
            StateStore store
    ) {
        this.engine = engine;
        this.repairs = repairs;
        this.verification = verification;
        this.reports = reports;
        this.store = store;
    }

    @PostMapping("/diagnostics")
    public DiagnosticModels.Run run(@RequestBody(required = false) ApiRequests.RunDiagnosticsRequest request) {
        String projectId = request == null ? null : request.projectId();
        return engine.run(projectId);
    }

    @GetMapping("/diagnostics/{id}")
    public DiagnosticModels.Run get(@PathVariable String id) {
        return engine.get(id);
    }

    /** Previous runs, newest first — powers the history panel. */
    @GetMapping("/diagnostics")
    public List<DiagnosticModels.RunRef> history(@RequestParam(defaultValue = "20") int limit) {
        return engine.history(Math.min(Math.max(limit, 1), 100));
    }

    @PostMapping("/diagnostics/{id}/repair-preview")
    public DiagnosticModels.RepairSuggestion preview(
            @PathVariable String id,
            @RequestBody ApiRequests.RepairApprovalRequest request
    ) {
        requireFinding(request);
        return repairs.preview(id, request.findingId());
    }

    @PostMapping("/diagnostics/{id}/repair-approval")
    public DiagnosticModels.RepairResult approve(
            @PathVariable String id,
            @RequestBody ApiRequests.RepairApprovalRequest request
    ) {
        requireFinding(request);
        return repairs.apply(id, request.findingId(), request.restartContainers());
    }

    @PostMapping("/diagnostics/{id}/repair-rollback")
    public DiagnosticModels.RepairResult rollback(
            @PathVariable String id,
            @RequestBody ApiRequests.RepairApprovalRequest request
    ) {
        requireFinding(request);
        return repairs.rollback(id, request.findingId());
    }

    @PostMapping("/diagnostics/{id}/verify")
    public DiagnosticModels.VerificationResult verify(
            @PathVariable String id,
            @RequestBody ApiRequests.RepairApprovalRequest request
    ) {
        requireFinding(request);
        return verification.verify(id, request.findingId());
    }

    /** Every verification verdict, newest first. */
    @GetMapping("/verifications")
    public List<StateStore.VerificationRecord> verifications(@RequestParam(defaultValue = "20") int limit) {
        return verification.history(Math.min(Math.max(limit, 1), 200));
    }

    /** Shareable Markdown report of one run. */
    @GetMapping(value = "/diagnostics/{id}/report", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> report(@PathVariable String id) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown; charset=utf-8"))
                .body(reports.markdown(id));
    }

    private static void requireFinding(ApiRequests.RepairApprovalRequest request) {
        if (request == null || request.findingId() == null || request.findingId().isBlank()) {
            throw new IllegalArgumentException("findingId is required");
        }
    }
}
