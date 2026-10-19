package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.model.DockerModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class ContainerUnhealthyRule implements DiagnosticRule {

    @Override
    public String id() {
        return "container-unhealthy";
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        if (!context.dockerStatus().connected()) {
            findings.add(new DiagnosticModels.Finding(
                    UUID.randomUUID().toString(),
                    id(),
                    "Docker Engine is not connected",
                    "WARN",
                    context.dockerStatus().error() == null
                            ? "StackPilot could not reach Docker. Start Docker Desktop and retry."
                            : context.dockerStatus().error(),
                    List.of(new DiagnosticModels.Evidence("DOCKER", "engine", "disconnected")),
                    null
            ));
            return findings;
        }
        for (DockerModels.ContainerSummary container : context.containers()) {
            String state = container.state() == null ? "" : container.state().toLowerCase(Locale.ROOT);
            String status = container.status() == null ? "" : container.status().toLowerCase(Locale.ROOT);
            if (state.equals("exited") || state.equals("dead") || state.equals("restarting") || status.contains("unhealthy")) {
                String logs = context.logsByContainer().getOrDefault(container.id(), "");
                List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
                evidence.add(new DiagnosticModels.Evidence("DOCKER", container.name(), container.state() + " / " + container.status()));
                if (!logs.isBlank()) {
                    String tail = logs.strip();
                    int start = Math.max(0, tail.length() - 400);
                    evidence.add(new DiagnosticModels.Evidence("LOG", container.name(), tail.substring(start)));
                }
                findings.add(new DiagnosticModels.Finding(
                        UUID.randomUUID().toString(),
                        id(),
                        "Container `" + container.name() + "` is " + container.state(),
                        "WARN",
                        "Docker reports this container as " + container.status() + ". Recent logs are attached as evidence.",
                        evidence,
                        null
                ));
            }
        }
        return findings;
    }
}
