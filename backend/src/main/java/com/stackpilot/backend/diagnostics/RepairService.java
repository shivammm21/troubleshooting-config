package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.project.ProjectService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class RepairService {

    private final DiagnosticEngine engine;
    private final ProjectService projects;

    public RepairService(DiagnosticEngine engine, ProjectService projects) {
        this.engine = engine;
        this.projects = projects;
    }

    public DiagnosticModels.RepairSuggestion preview(String runId, String findingId) {
        DiagnosticModels.Finding finding = engine.finding(runId, findingId);
        if (finding.repair() == null || finding.repair().patches().isEmpty()) {
            throw new IllegalStateException("This finding has no automated repair");
        }
        return finding.repair();
    }

    public DiagnosticModels.RepairResult apply(String runId, String findingId) {
        DiagnosticModels.Run run = engine.get(runId);
        DiagnosticModels.Finding finding = engine.finding(runId, findingId);
        if (finding.repair() == null || finding.repair().patches().isEmpty()) {
            throw new IllegalStateException("This finding has no automated repair");
        }
        if (run.projectId() == null) {
            throw new IllegalStateException("Attach and index a project before applying a repair");
        }
        Path root = Path.of(projects.get(run.projectId()).rootPath());
        List<String> changed = new ArrayList<>();
        try {
            for (DiagnosticModels.FilePatch patch : finding.repair().patches()) {
                Path file = root.resolve(patch.path()).normalize();
                if (!file.startsWith(root)) {
                    throw new IllegalArgumentException("Patch path escapes the project root: " + patch.path());
                }
                if (!Files.isRegularFile(file)) {
                    throw new IllegalStateException("File not found: " + patch.path());
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (!content.contains(patch.originalSnippet())) {
                    throw new IllegalStateException("Original snippet no longer present in " + patch.path());
                }
                Path backup = Path.of(file + ".stackpilot.bak");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                String updated = content.replaceFirst(
                        java.util.regex.Pattern.quote(patch.originalSnippet()),
                        java.util.regex.Matcher.quoteReplacement(patch.replacementSnippet())
                );
                Files.writeString(file, updated, StandardCharsets.UTF_8);
                changed.add(patch.path());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to apply repair: " + e.getMessage());
        }
        return new DiagnosticModels.RepairResult(findingId, true, changed, "Repair applied. Re-index and verify to confirm.");
    }
}
