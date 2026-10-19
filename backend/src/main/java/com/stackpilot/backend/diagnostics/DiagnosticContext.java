package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.project.model.ProjectModels;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record DiagnosticContext(
        ProjectModels.Snapshot project,
        DockerModels.Status dockerStatus,
        List<DockerModels.ContainerSummary> containers,
        Map<String, String> logsByContainer
) {

    public String combinedLogs() {
        return logsByContainer.values().stream().collect(Collectors.joining("\n"));
    }

    public boolean composeHasService(String hint) {
        if (project == null) {
            return false;
        }
        String needle = hint.toLowerCase();
        return project.composeServices().stream().anyMatch(name -> name.toLowerCase().contains(needle));
    }

    public boolean hasNonInfraAppService() {
        if (project == null) {
            return false;
        }
        return project.composeServices().stream().anyMatch(name -> {
            String n = name.toLowerCase();
            return !n.contains("redis") && !n.contains("kafka") && !n.contains("zookeeper")
                    && !n.contains("postgres") && !n.contains("mysql");
        });
    }
}
