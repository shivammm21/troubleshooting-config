package com.stackpilot.backend.api.dto;

public final class ApiRequests {

    private ApiRequests() {
    }

    public record AttachProjectRequest(String path) {
    }

    public record RunDiagnosticsRequest(String projectId) {
    }

    /**
     * @param restart true when the developer also approved restarting the affected
     *                containers so a file change actually takes effect
     */
    public record RepairApprovalRequest(String findingId, Boolean restart) {

        /** Preview/verify/rollback calls carry no restart flag. */
        public RepairApprovalRequest(String findingId) {
            this(findingId, null);
        }

        public boolean restartContainers() {
            return Boolean.TRUE.equals(restart);
        }
    }
}
