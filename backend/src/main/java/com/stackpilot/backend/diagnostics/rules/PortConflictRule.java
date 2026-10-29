package com.stackpilot.backend.diagnostics.rules;

import com.stackpilot.backend.diagnostics.DiagnosticContext;
import com.stackpilot.backend.diagnostics.DiagnosticRule;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.project.model.ProjectModels;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects two Compose services publishing the same host port, which stops the
 * stack with "port is already allocated".
 */
@Component
public class PortConflictRule implements DiagnosticRule {

    private record Usage(String service, ProjectModels.ServicePort port, String file) {
    }

    @Override
    public String id() {
        return "port-conflict";
    }

    @Override
    public int order() {
        return 26;
    }

    @Override
    public List<DiagnosticModels.Finding> evaluate(DiagnosticContext context) {
        List<DiagnosticModels.Finding> findings = new ArrayList<>();
        if (context.project() == null || context.services().isEmpty()) {
            return findings;
        }

        Map<Integer, List<Usage>> byHostPort = new LinkedHashMap<>();
        for (ProjectModels.ComposeService service : context.services()) {
            for (ProjectModels.ServicePort port : service.ports()) {
                if (port.hostPort() <= 0) {
                    continue;
                }
                byHostPort.computeIfAbsent(port.hostPort(), key -> new ArrayList<>())
                        .add(new Usage(service.name(), port, service.file()));
            }
        }

        for (Map.Entry<Integer, List<Usage>> entry : byHostPort.entrySet()) {
            List<Usage> usages = entry.getValue();
            if (usages.size() < 2) {
                continue;
            }
            context.claim("PORT");

            int hostsPort = entry.getKey();
            int freePort = RuleSupport.nextFreePort(hostsPort + 1, context.publishedHostPorts());

            // Report every mapping after the first: the first one legitimately owns the port.
            for (int i = 1; i < usages.size(); i++) {
                Usage usage = usages.get(i);
                List<DiagnosticModels.Evidence> evidence = new ArrayList<>();
                evidence.add(RuleSupport.evidence("CONFIG",
                        usage.file() + " → services." + usage.service() + ".ports",
                        hostsPort + ":" + usage.port().containerPort() + " (also used by "
                                + usages.getFirst().service() + ")"));

                String needle = hostsPort + ":" + usage.port().containerPort();
                DiagnosticModels.RepairSuggestion repair = null;
                String content = context.file(usage.file())
                        .map(ProjectModels.IndexedFile::content)
                        .orElse("");
                if (RuleSupport.countOccurrences(content, needle) == 1) {
                    repair = DiagnosticModels.RepairSuggestion.of(
                            "Publish `" + usage.service() + "` on host port " + freePort
                                    + " instead of " + hostsPort + " and recreate the stack.",
                            List.of(new DiagnosticModels.FilePatch(usage.file(), needle,
                                    freePort + ":" + usage.port().containerPort())),
                            "MEDIUM",
                            true);
                }

                List<String> nextSteps = new ArrayList<>();
                if (repair == null) {
                    nextSteps.add("Edit " + usage.file() + " and give `" + usage.service()
                            + "` a different host port than " + usages.getFirst().service());
                }
                nextSteps.add("Recreate the stack: docker compose up -d");
                nextSteps.add("Re-run diagnostics to confirm the port conflict is gone");

                findings.add(new DiagnosticModels.Finding(
                        RuleSupport.id(),
                        id(),
                        "Host port " + hostsPort + " is published twice",
                        "ERROR",
                        "Both `" + usages.getFirst().service() + "` and `" + usage.service()
                                + "` map host port " + hostsPort + " in " + usage.file()
                                + ". Only one container can bind it, so the second one fails to start "
                                + "with \"port is already allocated\".",
                        evidence,
                        repair,
                        90,
                        "configuration",
                        "port-conflict|" + hostsPort + "|" + usage.service(),
                        List.copyOf(nextSteps)
                ));
            }
        }
        return findings;
    }
}
