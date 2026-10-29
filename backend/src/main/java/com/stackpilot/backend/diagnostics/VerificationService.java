package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.health.HealthService;
import com.stackpilot.backend.health.model.HealthModels;
import com.stackpilot.backend.project.ProjectService;
import com.stackpilot.backend.store.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Feature 6 – Post-Fix Verification.
 *
 * <p>Verification is deliberately two-layered, because a repair can succeed on
 * disk and still leave a broken stack:
 * <ol>
 *   <li><b>Static</b> – re-index the project files and re-run the rules. The
 *       diagnosis is only marked "fixed" when the same root cause stops firing.</li>
 *   <li><b>Runtime</b> – re-read the container states through {@code docker inspect}
 *       and re-run the TCP probes. A config that now looks right while the process
 *       is still restarting is reported as {@code CONFIG_FIXED_RUNTIME_PENDING}
 *       instead of a false "resolved".</li>
 * </ol>
 * Every verdict is persisted so the UI can show a real verification history.
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    /** How long to keep re-probing when a restart is still settling. */
    private static final long SETTLE_TIMEOUT_MS = 12_000;
    private static final long SETTLE_POLL_MS = 1_000;

    private final DiagnosticEngine engine;
    private final ProjectService projects;
    private final HealthService health;
    private final StateStore store;

    public VerificationService(
            DiagnosticEngine engine,
            ProjectService projects,
            HealthService health,
            StateStore store
    ) {
        this.engine = engine;
        this.projects = projects;
        this.health = health;
        this.store = store;
    }

    public DiagnosticModels.VerificationResult verify(String runId, String findingId) {
        DiagnosticModels.Run original = engine.get(runId);
        DiagnosticModels.Finding finding = engine.finding(runId, findingId);
        String projectId = original.projectId();

        if (projectId != null && !projectId.isBlank()) {
            try {
                projects.reindex(projectId);
            } catch (Exception e) {
                log.warn("Re-index before verification failed for {}: {}", projectId, e.toString());
            }
        }

        DiagnosticModels.Run next = engine.run(projectId);
        List<DiagnosticModels.Finding> remaining = sameRootCause(next, finding);

        HealthModels.Snapshot snapshot = health.snapshot();
        List<String> runtimeChecks = new ArrayList<>(runtimeChecks(finding, snapshot));
        boolean runtimeHealthy = runtimeHealthy(finding, snapshot);

        if (remaining.isEmpty() && !runtimeHealthy && snapshot.dockerConnected()) {
            // Give a restart the chance to finish before declaring a pending runtime.
            snapshot = settle(finding);
            runtimeChecks = new ArrayList<>(runtimeChecks(finding, snapshot));
            runtimeHealthy = runtimeHealthy(finding, snapshot);
        }

        String status = statusOf(finding, remaining, runtimeHealthy);
        String summary = summaryOf(finding, status, remaining, snapshot);

        DiagnosticModels.VerificationResult result = new DiagnosticModels.VerificationResult(
                // The verdict belongs to the run the developer is looking at, not to the
                // throw-away re-run used to re-evaluate the rules.
                runId,
                findingId,
                "RESOLVED".equals(status),
                status,
                summary,
                List.copyOf(remaining),
                List.copyOf(runtimeChecks),
                Instant.now()
        );

        store.addVerification(new StateStore.VerificationRecord(
                UUID.randomUUID().toString(),
                result.checkedAt(),
                projectId,
                runId,
                findingId,
                finding.ruleId(),
                result.resolved(),
                result.status(),
                result.summary()
        ));

        log.info("Verification of finding {} (rule {}) -> {}", findingId, finding.ruleId(), status);
        return result;
    }

    /** Latest verification verdicts, newest first. */
    public List<StateStore.VerificationRecord> history(int limit) {
        return store.verifications(Math.max(1, limit));
    }

    // ------------------------------------------------------------------
    // comparison
    // ------------------------------------------------------------------

    /**
     * A finding is "still there" when the same rule reports the same root cause,
     * or – for rules without a stable root cause key – when it still reports
     * anything at all for the same subject.
     */
    private static List<DiagnosticModels.Finding> sameRootCause(
            DiagnosticModels.Run run,
            DiagnosticModels.Finding original
    ) {
        String key = original.rootCauseKey();
        List<DiagnosticModels.Finding> matches = new ArrayList<>();
        for (DiagnosticModels.Finding candidate : run.findings()) {
            if (!original.ruleId().equals(candidate.ruleId())) {
                continue;
            }
            if (key == null || key.isBlank() || key.equals(candidate.rootCauseKey())) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    // ------------------------------------------------------------------
    // runtime checks
    // ------------------------------------------------------------------

    /** Container names/ids referenced by the original finding. */
    private static Set<String> targets(DiagnosticModels.Finding finding) {
        Set<String> names = new LinkedHashSet<>();
        for (DiagnosticModels.Evidence evidence : finding.evidence()) {
            if (!"DOCKER".equalsIgnoreCase(evidence.source()) || evidence.location() == null) {
                continue;
            }
            String location = evidence.location().trim();
            if (location.isEmpty()) {
                continue;
            }
            names.add(location);
            // "service (container)" style locations produced by DiagnosticContext.label()
            int open = location.indexOf('(');
            int close = location.indexOf(')');
            if (open > 0 && close > open + 1) {
                String inner = location.substring(open + 1, close).trim();
                if (!inner.isEmpty()) {
                    names.add(inner);
                }
                names.add(location.substring(0, open).trim());
            }
        }
        return names;
    }

    private static boolean matchesTarget(String name, Set<String> targets) {
        if (name == null) {
            return false;
        }
        String plain = name.replaceFirst("^/", "");
        for (String target : targets) {
            if (target.equalsIgnoreCase(plain) || target.equalsIgnoreCase("/" + plain)) {
                return true;
            }
            if (target.length() >= 6
                    && (plain.startsWith(target) || target.startsWith(plain))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> runtimeChecks(
            DiagnosticModels.Finding finding,
            HealthModels.Snapshot snapshot
    ) {
        List<String> checks = new ArrayList<>();
        if (!snapshot.dockerConnected()) {
            checks.add("Docker engine unreachable: "
                    + (snapshot.dockerError() == null ? "no response" : snapshot.dockerError()));
            return checks;
        }

        Set<String> targets = targets(finding);
        boolean scoped = !targets.isEmpty();
        String scope = scoped ? "target container(s)" : "all containers";

        for (DockerModels.ContainerSummary container : snapshot.containers()) {
            if (scoped && !matchesTarget(container.name(), targets)) {
                continue;
            }
            checks.add("Container `" + container.name() + "`: state " + container.state()
                    + (container.health() == null ? "" : ", health " + container.health())
                    + (container.exitCode() == null || container.exitCode() == 0
                    ? "" : ", exit code " + container.exitCode()));
        }

        for (HealthModels.Dependency dependency : snapshot.dependencies()) {
            if (scoped && !matchesTarget(dependency.container(), targets)) {
                continue;
            }
            checks.add((dependency.reachable() ? "Reachable: " : "Not reachable: ")
                    + "`" + dependency.service() + "` on 127.0.0.1:" + dependency.hostPort()
                    + (dependency.reachable() && dependency.latencyMs() >= 0
                    ? " (" + dependency.latencyMs() + "ms)" : ""));
        }

        if (checks.isEmpty()) {
            checks.add("No runtime indicator available for " + scope
                    + "; the verdict relies on container state and logs only.");
        }
        return checks;
    }

    private static boolean runtimeHealthy(
            DiagnosticModels.Finding finding,
            HealthModels.Snapshot snapshot
    ) {
        if (!snapshot.dockerConnected()) {
            return false;
        }
        Set<String> targets = targets(finding);
        if (targets.isEmpty()) {
            // Nothing to scope to: any broken dependency in the stack counts.
            return snapshot.dependencies().stream().noneMatch(HealthModels.Dependency::broken)
                    && snapshot.unhealthyContainers() == 0;
        }

        boolean sawContainer = false;
        for (DockerModels.ContainerSummary container : snapshot.containers()) {
            if (!matchesTarget(container.name(), targets)) {
                continue;
            }
            sawContainer = true;
            if (container.stopped() || container.unhealthy()) {
                return false;
            }
            if ("restarting".equalsIgnoreCase(container.state())) {
                return false;
            }
        }

        for (HealthModels.Dependency dependency : snapshot.dependencies()) {
            if (matchesTarget(dependency.container(), targets) && dependency.broken()) {
                return false;
            }
        }
        if (sawContainer) {
            return true;
        }
        // The container may have been recreated under a new name: fall back to a
        // stack-wide check instead of claiming a bogus success.
        return snapshot.containers().stream().noneMatch(c -> c.stopped() || c.unhealthy());
    }

    /** Polls the runtime briefly so a container restart is not reported as broken. */
    private HealthModels.Snapshot settle(DiagnosticModels.Finding finding) {
        long deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS;
        HealthModels.Snapshot last = health.snapshot();
        if (!last.dockerConnected() || runtimeHealthy(finding, last)) {
            return last;
        }
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(SETTLE_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
            last = health.snapshot();
            if (runtimeHealthy(finding, last)) {
                return last;
            }
        }
        return last;
    }

    // ------------------------------------------------------------------
    // verdict
    // ------------------------------------------------------------------

    private static String statusOf(
            DiagnosticModels.Finding finding,
            List<DiagnosticModels.Finding> remaining,
            boolean runtimeHealthy
    ) {
        if (!remaining.isEmpty()) {
            return finding.fixable() ? "UNRESOLVED" : "NO_FIX";
        }
        return runtimeHealthy ? "RESOLVED" : "CONFIG_FIXED_RUNTIME_PENDING";
    }

    private static String summaryOf(
            DiagnosticModels.Finding finding,
            String status,
            List<DiagnosticModels.Finding> remaining,
            HealthModels.Snapshot snapshot
    ) {
        return switch (status) {
            case "RESOLVED" -> "Fixed. Rule `" + finding.ruleId() + "` no longer reports this root cause and "
                    + "the runtime indicators (container state and TCP probes) are healthy.";
            case "CONFIG_FIXED_RUNTIME_PENDING" -> snapshot.dockerConnected()
                    ? "The configuration is fixed, but the runtime is not healthy yet"
                    + containerHint(snapshot) + ". Restart or start the affected container and verify again."
                    : "The configuration is fixed, but Docker is unreachable so the runtime could not be "
                    + "verified. Start Docker and run verification again.";
            case "NO_FIX" -> "The problem persists and there is no automated patch for it. Follow the manual steps: "
                    + (finding.nextSteps().isEmpty() ? "inspect the evidence and correct the configuration."
                    : String.join(" | ", finding.nextSteps()));
            default -> "The same root cause is still detected: " + remaining.getFirst().summary();
        };
    }

    private static String containerHint(HealthModels.Snapshot snapshot) {
        List<String> broken = new ArrayList<>();
        for (DockerModels.ContainerSummary container : snapshot.containers()) {
            if (container.stopped() || container.unhealthy()) {
                broken.add(container.name() + " is " + container.state()
                        + (container.health() == null ? "" : "/" + container.health()));
            }
        }
        for (HealthModels.Dependency dependency : snapshot.dependencies()) {
            if (dependency.broken()) {
                broken.add(dependency.detail());
            }
        }
        if (broken.isEmpty()) {
            return "";
        }
        return " (" + String.join("; ", broken.stream().limit(3).toList()) + ")";
    }

    /** Lowercase convenience used by the desktop client. */
    public static boolean isResolved(String status) {
        return status != null && "RESOLVED".equals(status.toUpperCase(Locale.ROOT));
    }
}
