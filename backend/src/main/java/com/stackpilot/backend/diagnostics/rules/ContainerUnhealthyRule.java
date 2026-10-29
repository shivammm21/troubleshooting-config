package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.logs.model.LogModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reports containers that are stopped, restarting or reported unhealthy by their
 * Docker healthcheck, and explains the exit code when there is one.
 */
@Component
public class ContainerUnhealthyRule implements DiagnosticRule {

    @Override
    public String id() {
        return "container-unhealthy";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String category() {
        return "infra";
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();

        if (!context.dockerStatus().connected()) {
            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "Docker Engine is not connected",
                    "WARN",
                    context.dockerStatus().error() == null
                            ? "StackPilot could not reach Docker. Start Docker Desktop (or set DOCKER_HOST) and retry."
                            : context.dockerStatus().error(),
                    List.of(RuleSupport.evidence("DOCKER", "engine", "disconnected")),
                    null,
                    95,
                    "infra",
                    "docker-disconnected",
                    List.of("Start Docker Desktop", "Confirm `docker ps` works in a terminal", "Re-run diagnostics")
            ));
            return findings;
        }

        for (DockerModels.ContainerSummary container : context.containers()) {
            DiagnosticModels.Finding finding = evaluateContainer(context, container);
            if (finding != null) {
                findings.add(finding);
            }
        }
        return findings;
    }

    private DiagnosticModels.Finding evaluateContainer(
            DiagnosticContext context,
            DockerModels.ContainerSummary container
    ) {
        String state = container.state() == null ? "" : container.state().toLowerCase(Locale.ROOT);
        boolean unhealthy = container.unhealthy();
        boolean restarting = "restarting".equals(state);
        boolean stopped = container.stopped();

        if (!unhealthy && !restarting && !stopped) {
            return null;
        }

        String name = container.name() == null ? container.id() : container.name();
        context.claim("container:" + name);

        List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
        evidence.add(RuleSupport.evidence("DOCKER", name,
                state + " / " + container.status()
                        + (container.exitCode() == null ? "" : " / exit " + container.exitCode())
                        + (container.restartCount() > 0 ? " / restarts " + container.restartCount() : "")));

        LogModels.Summary summary = context.summaryFor(container);
        if (!summary.signatures().isEmpty()) {
            LogModels.Signature top = summary.signatures().getFirst();
            evidence.add(RuleSupport.evidence("LOG",
                    DiagnosticContext.label(container) + " line " + top.line(),
                    top.sample()));
        } else {
            String logs = context.logsByContainer().getOrDefault(container.id(), "");
            if (!logs.isBlank()) {
                String tail = logs.strip();
                int start = Math.max(0, tail.length() - 400);
                evidence.add(RuleSupport.evidence("LOG", DiagnosticContext.label(container), tail.substring(start)));
            }
        }

        String severity;
        String title;
        String summaryText;
        List<String> nextSteps = new ArrayList<>();
        DiagnosticModels.RepairSuggestion repair = null;
        int confidence = 90;

        if (stopped) {
            severity = "ERROR";
            title = "Container `" + name + "` is " + state;
            Integer exitCode = container.exitCode();
            String hint = exitHint(exitCode);
            summaryText = "Docker reports this container as " + container.status() + "."
                    + (exitCode == null ? "" : " Last exit code: " + exitCode + ". " + hint)
                    + (summary.signatures().isEmpty()
                    ? " No error signature was found in the captured log tail."
                    : " The strongest log signature is: " + summary.signatures().getFirst().signature());
            if (exitCode != null && exitCode == 137) {
                context.claim("OOM");
                confidence = 92;
            }
            nextSteps.add("Inspect the full log: docker logs --tail 200 " + name);
            nextSteps.add("Start it again once the cause is fixed (StackPilot can start it for you)");
            if (exitCode != null && exitCode == 137) {
                nextSteps.add("Raise the container memory limit (exit 137 means it was killed)");
            }
            repair = DiagnosticModels.RepairSuggestion.action(
                    "Start the container after the underlying configuration issue is fixed.",
                    new DiagnosticModels.RepairAction("START_CONTAINER", container.id(), name),
                    "MEDIUM");
        } else if (unhealthy) {
            severity = "ERROR";
            title = "Container `" + name + "` reports unhealthy";
            summaryText = "Docker's healthcheck for this container is failing ("
                    + container.status() + "). The service is running but not accepting traffic.";
            nextSteps.add("Check the healthcheck command in docker-compose and the container logs");
            nextSteps.add("Restart the container once its dependency is reachable");
            repair = DiagnosticModels.RepairSuggestion.action(
                    "Restart the unhealthy container so its healthcheck runs again.",
                    new DiagnosticModels.RepairAction("RESTART_CONTAINER", container.id(), name),
                    "MEDIUM");
        } else {
            severity = container.restartCount() > 3 ? "ERROR" : "WARN";
            title = "Container `" + name + "` keeps restarting";
            summaryText = "Docker reports this container in the restarting state after "
                    + container.restartCount() + " restart(s). A crash loop usually means a bad "
                    + "configuration value or an unreachable dependency.";
            nextSteps.add("Read the container logs to find the crash reason");
            nextSteps.add("Fix the configuration, then restart");
            repair = DiagnosticModels.RepairSuggestion.action(
                    "Restart the container after the configuration is corrected.",
                    new DiagnosticModels.RepairAction("RESTART_CONTAINER", container.id(), name),
                    "MEDIUM");
        }

        return new DiagnosticModels.Finding(
                RuleSupport.id(),
                id(),
                title,
                severity,
                summaryText,
                evidence,
                repair,
                confidence,
                "infra",
                "container:" + name,
                List.copyOf(nextSteps)
        );
    }

    private static String exitHint(Integer exitCode) {
        if (exitCode == null) {
            return "";
        }
        return switch (exitCode) {
            case 0 -> "The container exited cleanly, so the entrypoint probably finished.";
            case 1 -> "Exit 1 is an application error — check the log tail above.";
            case 126, 127 -> "Exit " + exitCode + " means the entrypoint command was not found or not executable.";
            case 137 -> "Exit 137 means the container was killed (OOM or `docker kill`).";
            case 139 -> "Exit 139 means a segmentation fault in the entrypoint process.";
            case 143 -> "Exit 143 means the process received SIGTERM (usually a manual stop).";
            default -> "Exit code " + exitCode + " — see the container log for the failing step.";
        };
    }
}
