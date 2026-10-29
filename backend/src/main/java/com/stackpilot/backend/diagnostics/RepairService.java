package com.stackpilot.backend.diagnostics;

import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.docker.DockerEngine;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.project.ProjectService;
import com.stackpilot.backend.project.model.ProjectModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Feature 5 – Smart Fix Recommendations (apply half).
 *
 * <p>Nothing is written until the developer approves a specific finding. Every
 * mutated file is copied to {@code <file>.stackpilot.bak} first so
 * {@link #rollback(String, String)} can restore it, and container actions
 * (start/restart) run only as part of an approved repair.
 */
@Service
public class RepairService {

    private static final Logger log = LoggerFactory.getLogger(RepairService.class);

    private static final String BACKUP_SUFFIX = ".stackpilot.bak";

    private final DiagnosticEngine engine;
    private final ProjectService projects;
    private final DockerEngine docker;

    public RepairService(DiagnosticEngine engine, ProjectService projects, DockerEngine docker) {
        this.engine = engine;
        this.projects = projects;
        this.docker = docker;
    }

    public DiagnosticModels.RepairSuggestion preview(String runId, String findingId) {
        DiagnosticModels.Finding finding = engine.finding(runId, findingId);
        if (finding.repair() == null || finding.repair().isEmpty()) {
            throw new IllegalStateException("This finding has no automated repair");
        }
        return finding.repair();
    }

    /**
     * @param restartContainers also restart the affected containers so the new
     *                          configuration is actually loaded
     */
    public DiagnosticModels.RepairResult apply(String runId, String findingId, boolean restartContainers) {
        DiagnosticModels.Run run = engine.get(runId);
        DiagnosticModels.Finding finding = engine.finding(runId, findingId);
        DiagnosticModels.RepairSuggestion repair = finding.repair();
        if (repair == null || repair.isEmpty()) {
            throw new IllegalStateException("This finding has no automated repair");
        }

        List<String> changed = new ArrayList<>();
        List<String> backups = new ArrayList<>();
        List<String> restarted = new ArrayList<>();

        if (repair.hasFileChanges()) {
            if (run.projectId() == null) {
                throw new IllegalStateException("Attach and index a project before applying a repair");
            }
            Path root = Path.of(projects.get(run.projectId()).rootPath());
            for (DiagnosticModels.FilePatch patch : repair.patches()) {
                applyPatch(root, patch, changed, backups);
            }
        }

        // Explicit container actions are the fix itself, so always run them.
        for (DiagnosticModels.RepairAction action : safe(repair.actions())) {
            String kind = action.kind() == null ? "" : action.kind().toUpperCase(Locale.ROOT);
            String target = action.target();
            if (target == null || target.isBlank()) {
                continue;
            }
            boolean ok = switch (kind) {
                case "RESTART_CONTAINER" -> docker.restart(target);
                case "START_CONTAINER" -> docker.start(target);
                default -> false;
            };
            if (ok) {
                restarted.add(action.description() == null ? target : action.description());
            }
        }

        if (restartContainers && repair.hasFileChanges()) {
            restarted.addAll(restartProjectContainers(run.projectId()));
        } else if (repair.requiresRestart() && repair.hasFileChanges() && !restartContainers) {
            log.info("Repair applied without restart; the change only takes effect after a container restart");
        }

        String message = buildMessage(changed, restarted, repair.requiresRestart(), restartContainers);
        return new DiagnosticModels.RepairResult(
                findingId, true, changed, backups, restarted, message);
    }

    /** Restores every backup created while applying this finding. */
    public DiagnosticModels.RepairResult rollback(String runId, String findingId) {
        DiagnosticModels.Run run = engine.get(runId);
        DiagnosticModels.Finding finding = engine.finding(runId, findingId);
        DiagnosticModels.RepairSuggestion repair = finding.repair();
        if (repair == null || !repair.hasFileChanges()) {
            throw new IllegalStateException("This finding has no file changes to roll back");
        }
        if (run.projectId() == null) {
            throw new IllegalStateException("Attach and index a project before rolling back a repair");
        }

        Path root = Path.of(projects.get(run.projectId()).rootPath());
        List<String> restored = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (DiagnosticModels.FilePatch patch : repair.patches()) {
            Path file = resolveSafely(root, patch.path());
            Path backup = Path.of(file + BACKUP_SUFFIX);
            if (!Files.isRegularFile(backup)) {
                missing.add(patch.path());
                continue;
            }
            try {
                Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
                restored.add(patch.path());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to restore " + patch.path() + ": " + e.getMessage());
            }
        }

        if (restored.isEmpty()) {
            throw new IllegalStateException("No backups found for this finding: " + String.join(", ", missing));
        }

        projects.reindex(run.projectId());
        String message = "Restored " + restored.size() + " file(s) from backup."
                + (missing.isEmpty() ? "" : " No backup for: " + String.join(", ", missing));
        return new DiagnosticModels.RepairResult(
                findingId, true, restored, List.of(), List.of(), message);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void applyPatch(
            Path root,
            DiagnosticModels.FilePatch patch,
            List<String> changed,
            List<String> backups
    ) {
        String mode = patch.mode() == null ? "REPLACE" : patch.mode().toUpperCase(Locale.ROOT);
        Path file = resolveSafely(root, patch.path());

        try {
            switch (mode) {
                case "CREATE_FILE" -> {
                    if (Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8)
                            .contains(patch.replacementSnippet())) {
                        return;
                    }
                    backup(file, backups, patch.path());
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, patch.replacementSnippet(), StandardCharsets.UTF_8);
                    changed.add(patch.path());
                }
                case "APPEND_LINE" -> {
                    String existing = Files.isRegularFile(file)
                            ? Files.readString(file, StandardCharsets.UTF_8)
                            : "";
                    String line = patch.replacementSnippet() == null ? "" : patch.replacementSnippet().strip();
                    if (line.isEmpty()) {
                        return;
                    }
                    // Do not add a duplicate definition.
                    boolean alreadyPresent = existing.lines()
                            .map(String::strip)
                            .anyMatch(stripped -> stripped.equals(line));
                    if (alreadyPresent) {
                        return;
                    }
                    backup(file, backups, patch.path());
                    Files.createDirectories(file.getParent() == null ? root : file.getParent());
                    String prefix = existing.isEmpty() || existing.endsWith("\n") ? "" : "\n";
                    Files.writeString(file, existing + prefix + line + "\n", StandardCharsets.UTF_8);
                    changed.add(patch.path());
                }
                default -> {
                    if (!Files.isRegularFile(file)) {
                        throw new IllegalStateException("File not found: " + patch.path());
                    }
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    String original = patch.originalSnippet() == null ? "" : patch.originalSnippet();
                    if (original.isEmpty() || !content.contains(original)) {
                        throw new IllegalStateException("Original snippet no longer present in " + patch.path());
                    }
                    backup(file, backups, patch.path());
                    String updated = content.replaceFirst(
                            java.util.regex.Pattern.quote(original),
                            java.util.regex.Matcher.quoteReplacement(
                                    patch.replacementSnippet() == null ? "" : patch.replacementSnippet()));
                    Files.writeString(file, updated, StandardCharsets.UTF_8);
                    changed.add(patch.path());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to apply repair to " + patch.path() + ": " + e.getMessage());
        }
    }

    private static void backup(Path file, List<String> backups, String relativePath) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Path backup = Path.of(file + BACKUP_SUFFIX);
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        backups.add(relativePath + BACKUP_SUFFIX);
    }

    private static Path resolveSafely(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Patch path is required");
        }
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Patch path escapes the project root: " + relativePath);
        }
        return file;
    }

    /** Restarts the running containers that belong to the attached project. */
    private List<String> restartProjectContainers(String projectId) {
        List<String> restarted = new ArrayList<>();
        if (projectId == null) {
            return restarted;
        }
        ProjectModels.Snapshot project;
        try {
            project = projects.get(projectId);
        } catch (Exception e) {
            return restarted;
        }
        Set<String> services = new LinkedHashSet<>();
        project.services().forEach(service -> services.add(service.name().toLowerCase(Locale.ROOT)));

        List<DockerModels.ContainerSummary> containers = docker.listContainers();
        for (DockerModels.ContainerSummary container : containers) {
            if (!container.running() || container.infrastructure()) {
                continue;
            }
            String serviceName = container.composeService() == null
                    ? "" : container.composeService().toLowerCase(Locale.ROOT);
            boolean belongs = !services.isEmpty() && services.contains(serviceName);
            if (!belongs && !services.isEmpty()) {
                continue;
            }
            if (docker.restart(container.id())) {
                restarted.add(container.name());
            }
        }
        return restarted;
    }

    private static String buildMessage(
            List<String> changed,
            List<String> restarted,
            boolean requiresRestart,
            boolean didRestart
    ) {
        StringBuilder message = new StringBuilder();
        if (changed.isEmpty() && restarted.isEmpty()) {
            message.append("Nothing was changed (the fix was already applied).");
            return message.toString();
        }
        if (!changed.isEmpty()) {
            message.append("Updated ").append(String.join(", ", changed)).append(". ");
        }
        if (!restarted.isEmpty()) {
            message.append("Restarted: ").append(String.join(", ", restarted)).append(". ");
        } else if (requiresRestart && !didRestart) {
            message.append("Restart the affected container(s) so the change takes effect. ");
        }
        message.append("Verification runs automatically.");
        return message.toString();
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
