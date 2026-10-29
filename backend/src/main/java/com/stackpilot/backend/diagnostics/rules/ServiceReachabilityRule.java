package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.health.model.HealthModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Uses the active TCP probes: a container can be "running" and still not serve
 * traffic. Stopped/unhealthy containers are already reported by
 * {@link ContainerUnhealthyRule}, so this rule only speaks up for the gap where
 * the container looks fine but the published port refuses connections.
 */
@Component
public class ServiceReachabilityRule implements DiagnosticRule {

    @Override
    public String id() {
        return "service-unreachable";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String category() {
        return "infra";
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        if (!context.dockerStatus().connected()) {
            return findings;
        }

        for (HealthModels.Dependency dependency : context.dependencies()) {
            if (dependency.reachable()) {
                continue;
            }
            if (dependency.containerState() == null
                    || !dependency.containerState().equalsIgnoreCase("running")) {
                continue;
            }
            if ("unhealthy".equalsIgnoreCase(dependency.health())) {
                continue;
            }
            if (context.claimed("PROBE:" + dependency.service())) {
                continue;
            }
            context.claim("PROBE:" + dependency.service());

            Optional<DockerModels.ContainerSummary> container = context.containerByName(dependency.container());
            boolean hasErrorLogs = container.isPresent()
                    && context.summaryFor(container.get()).hasErrors();

            List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
            evidence.add(RuleSupport.evidence("PROBE", dependency.service(), dependency.detail()));
            evidence.add(RuleSupport.evidence("DOCKER", dependency.container(),
                    (dependency.containerState() == null ? "unknown" : dependency.containerState())
                            + " / health " + (dependency.health() == null ? "none" : dependency.health())));

            if (container.isPresent() && hasErrorLogs) {
                var summary = context.summaryFor(container.get());
                var top = summary.signatures().getFirst();
                evidence.add(RuleSupport.evidence("LOG",
                        DiagnosticContext.label(container.get()) + " line " + top.line(),
                        top.sample()));
            }

            List<String> nextSteps = new ArrayList<>();
            nextSteps.add("Check the container log for a startup failure");
            nextSteps.add("Confirm the service listens on the container port, not only on loopback inside the image");
            nextSteps.add("Restart the container once the cause is understood");

            DiagnosticModels.RepairSuggestion repair = container
                    .map(value -> DiagnosticModels.RepairSuggestion.action(
                            "Restart the container so it resumes serving traffic.",
                            new DiagnosticModels.RepairAction("RESTART_CONTAINER", value.id(), value.name()),
                            "MEDIUM"))
                    .orElse(null);

            findings.add(new DiagnosticModels.Finding(
                    RuleSupport.id(),
                    id(),
                    "`" + dependency.service() + "` is running but not reachable",
                    hasErrorLogs ? "ERROR" : "WARN",
                    "Docker reports the container as running, but a TCP probe of 127.0.0.1:"
                            + dependency.hostPort() + " was refused. The process is up without a "
                            + "listener on the published port.",
                    evidence,
                    repair,
                    hasErrorLogs ? 85 : 70,
                    "runtime",
                    "probe:" + dependency.service(),
                    List.copyOf(nextSteps)
            ));
        }
        return findings;
    }
}
