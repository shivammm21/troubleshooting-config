package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.health.HealthService;
import com.stackpilot.backend.health.model.HealthModels;
import com.stackpilot.backend.store.StateStore;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a diagnostic run as a Markdown report.
 *
 * <p>The report is the shareable artefact of the tool: what was wrong, which
 * evidence proved it, what was fixed, and whether the fix actually held. It is
 * exported as {@code text/markdown} by {@code /api/diagnostics/{id}/report}.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final DiagnosticEngine engine;
    private final HealthService health;
    private final StateStore store;

    public ReportService(DiagnosticEngine engine, HealthService health, StateStore store) {
        this.engine = engine;
        this.health = health;
        this.store = store;
    }

    public String markdown(String runId) {
        DiagnosticModels.Run run = engine.get(runId);
        HealthModels.Snapshot snapshot = health.snapshot();
        List<StateStore.VerificationRecord> verifications = store.verifications(200).stream()
                .filter(record -> runId.equals(record.runId()))
                .toList();

        StringBuilder out = new StringBuilder(4096);
        out.append("# StackPilot diagnostic report\n\n");
        out.append("| | |\n|---|---|\n");
        out.append("| Run | `").append(run.id()).append("` |\n");
        out.append("| Generated | ").append(STAMP.format(run.createdAt())).append(" |\n");
        out.append("| Project | ").append(run.projectId() == null ? "_not attached_" : "`" + run.projectId() + "`")
                .append(" |\n");
        if (run.summary() != null) {
            out.append("| Findings | ").append(run.summary().total())
                    .append(" (").append(run.summary().errors()).append(" errors, ")
                    .append(run.summary().warnings()).append(" warnings, ")
                    .append(run.summary().repairable()).append(" auto-fixable) |\n");
        }
        if (run.logHealth() != null) {
            out.append("| Logs | ").append(run.logHealth().containers()).append(" container(s), ")
                    .append(run.logHealth().totalLines()).append(" lines, ")
                    .append(run.logHealth().errorLines()).append(" error line(s) |\n");
        }
        out.append('\n');

        out.append("## Infrastructure health\n\n");
        out.append("Overall: **").append(snapshot.overall()).append("**")
                .append(snapshot.dockerConnected() ? "" : " (Docker unreachable)")
                .append("\n\n");
        if (snapshot.issues().isEmpty()) {
            out.append("No infrastructure issue detected.\n\n");
        } else {
            for (String issue : snapshot.issues()) {
                out.append("- ").append(issue).append('\n');
            }
            out.append('\n');
        }

        out.append("## Root cause analysis\n\n");
        if (run.findings().isEmpty()) {
            out.append("No problem was detected in this pass.\n\n");
        }
        int index = 1;
        for (DiagnosticModels.Finding finding : run.findings()) {
            out.append("### ").append(index++).append(". ").append(finding.title()).append("\n\n");
            out.append("- Severity: **").append(finding.severity()).append("**")
                    .append(" · confidence ").append(finding.confidence()).append("%")
                    .append(" · category ").append(finding.category()).append('\n');
            out.append("- Rule: `").append(finding.ruleId()).append("`\n");
            out.append("- Finding id: `").append(finding.id()).append("`\n\n");
            out.append(finding.summary()).append("\n\n");

            if (!finding.evidence().isEmpty()) {
                out.append("**Evidence**\n\n");
                for (DiagnosticModels.Evidence evidence : finding.evidence()) {
                    out.append("- `").append(evidence.source()).append("` ")
                            .append(evidence.location()).append(" — ")
                            .append(oneLine(evidence.excerpt())).append('\n');
                }
                out.append('\n');
            }

            DiagnosticModels.RepairSuggestion repair = finding.repair();
            if (repair != null && !repair.isEmpty()) {
                out.append("**Recommended fix** (").append(repair.risk()).append(" risk)\n\n");
                out.append(repair.description()).append("\n\n");
                for (DiagnosticModels.FilePatch patch : repair.patches()) {
                    out.append("- `").append(patch.mode()).append("` ").append(patch.path()).append('\n');
                    if (patch.mode() != null && !"APPEND_LINE".equals(patch.mode())
                            && !"CREATE_FILE".equals(patch.mode())) {
                        out.append("  - from: `").append(oneLine(patch.originalSnippet())).append("`\n");
                    }
                    out.append("  - to: `").append(oneLine(patch.replacementSnippet())).append("`\n");
                }
                for (DiagnosticModels.RepairAction action : repair.actions()) {
                    out.append("- `").append(action.kind()).append("` ").append(action.target()).append('\n');
                }
                out.append('\n');
            } else if (!finding.nextSteps().isEmpty()) {
                out.append("**Manual steps**\n\n");
                for (String step : finding.nextSteps()) {
                    out.append("- ").append(step).append('\n');
                }
                out.append('\n');
            }
        }

        out.append("## Post-fix verification\n\n");
        if (verifications.isEmpty()) {
            out.append("No verification has been run for this diagnostic run yet.\n\n");
        } else {
            out.append("| Checked | Rule | Status | Summary |\n|---|---|---|---|\n");
            for (StateStore.VerificationRecord record : verifications) {
                out.append("| ").append(STAMP.format(record.at()))
                        .append(" | `").append(record.ruleId()).append("`")
                        .append(" | ").append(record.status())
                        .append(" | ").append(oneLine(record.summary())).append(" |\n");
            }
            out.append('\n');
        }

        out.append("---\n\nGenerated by StackPilot — Docker troubleshooting assistant.\n");
        return out.toString();
    }

    /** Keeps table cells and bullet lists on one line. */
    private static String oneLine(String value) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('\r', ' ').replace('\n', ' ').replace('|', '/').strip();
        return flat.length() > 300 ? flat.substring(0, 297) + "..." : flat;
    }
}
