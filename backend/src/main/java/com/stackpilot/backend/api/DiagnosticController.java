package com.stackpilot.backend.api;

import com.stackpilot.backend.api.dto.ApiRequests;
import com.stackpilot.backend.diagnostics.DiagnosticEngine;
import com.stackpilot.backend.diagnostics.RepairService;
import com.stackpilot.backend.diagnostics.VerificationService;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DiagnosticController {

    private final DiagnosticEngine engine;
    private final RepairService repairs;
    private final VerificationService verification;

    public DiagnosticController(
            DiagnosticEngine engine,
            RepairService repairs,
            VerificationService verification
    ) {
        this.engine = engine;
        this.repairs = repairs;
        this.verification = verification;
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
        return repairs.apply(id, request.findingId());
    }

    @PostMapping("/diagnostics/{id}/verify")
    public DiagnosticModels.VerificationResult verify(
            @PathVariable String id,
            @RequestBody ApiRequests.RepairApprovalRequest request
    ) {
        requireFinding(request);
        return verification.verify(id, request.findingId());
    }

    private static void requireFinding(ApiRequests.RepairApprovalRequest request) {
        if (request == null || request.findingId() == null || request.findingId().isBlank()) {
            throw new IllegalArgumentException("findingId is required");
        }
    }
}
