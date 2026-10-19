package com.stackpilot.backend.api.dto;

public final class ApiRequests {

    private ApiRequests() {
    }

    public record AttachProjectRequest(String path) {
    }

    public record RunDiagnosticsRequest(String projectId) {
    }

    public record RepairApprovalRequest(String findingId) {
    }
}
