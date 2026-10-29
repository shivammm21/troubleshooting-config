package com.stackpilot.backend.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackpilot.backend.diagnostics.model.DiagnosticModels;
import com.stackpilot.backend.project.model.ProjectModels;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Durable state for projects, diagnostic runs and verification results.
 *
 * <p>A single JSON document under {@code ~/.stackpilot/state.json} keeps the tool
 * useful across restarts: the project list, the last runs and every verification
 * verdict survive closing the desktop app.
 */
@Service
public class StateStore {

    private static final Logger log = LoggerFactory.getLogger(StateStore.class);

    private static final int MAX_RUNS = 40;
    private static final int MAX_VERIFICATIONS = 200;

    /** One verification verdict, so the UI can show a real history. */
    public record VerificationRecord(
            String id,
            Instant at,
            String projectId,
            String runId,
            String findingId,
            String ruleId,
            boolean resolved,
            String status,
            String summary
    ) {
    }

    /** On-disk document. */
    public record Persisted(
            List<ProjectModels.ProjectRef> projects,
            List<DiagnosticModels.Run> runs,
            List<VerificationRecord> verifications
    ) {
    }

    private final ObjectMapper mapper;
    private final Path file;

    private final Map<String, ProjectModels.ProjectRef> projects = new ConcurrentHashMap<>();
    private final Map<String, DiagnosticModels.Run> runs = new ConcurrentHashMap<>();
    private final List<VerificationRecord> verifications = new CopyOnWriteArrayList<>();

    private volatile boolean writable = true;

    public StateStore(ObjectMapper mapper) {
        this.mapper = mapper;
        this.file = resolveFile();
    }

    private static Path resolveFile() {
        // System property first (tests point this at a scratch directory so a test
        // run never pollutes the developer's real history), then the env override.
        String override = System.getProperty("stackpilot.state.dir");
        if (override == null || override.isBlank()) {
            override = System.getenv("STACKPILOT_STATE_DIR");
        }
        Path dir = (override == null || override.isBlank())
                ? Path.of(System.getProperty("user.home"), ".stackpilot")
                : Path.of(override);
        return dir.resolve("state.json");
    }

    @PostConstruct
    void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Persisted persisted = mapper.readValue(Files.readString(file), Persisted.class);
            if (persisted == null) {
                return;
            }
            if (persisted.projects() != null) {
                for (ProjectModels.ProjectRef ref : persisted.projects()) {
                    if (ref != null && ref.id() != null && ref.rootPath() != null) {
                        projects.put(ref.id(), ref);
                    }
                }
            }
            if (persisted.runs() != null) {
                for (DiagnosticModels.Run run : persisted.runs()) {
                    if (run != null && run.id() != null) {
                        runs.put(run.id(), run);
                    }
                }
            }
            if (persisted.verifications() != null) {
                verifications.addAll(persisted.verifications());
            }
            log.info("Loaded state: {} project(s), {} run(s), {} verification(s) from {}",
                    projects.size(), runs.size(), verifications.size(), file);
        } catch (Exception e) {
            log.warn("Ignoring unreadable state file {}: {}", file, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // projects
    // ------------------------------------------------------------------

    public void putProject(ProjectModels.ProjectRef ref) {
        if (ref == null || ref.id() == null) {
            return;
        }
        projects.put(ref.id(), ref);
        save();
    }

    public Optional<ProjectModels.ProjectRef> project(String id) {
        return Optional.ofNullable(projects.get(id));
    }

    public List<ProjectModels.ProjectRef> projects() {
        return List.copyOf(projects.values());
    }

    // ------------------------------------------------------------------
    // runs
    // ------------------------------------------------------------------

    public void putRun(DiagnosticModels.Run run) {
        if (run == null || run.id() == null) {
            return;
        }
        runs.put(run.id(), run);
        trimRuns();
        save();
    }

    public Optional<DiagnosticModels.Run> run(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    /** Newest first. */
    public List<DiagnosticModels.Run> runs(int limit) {
        List<DiagnosticModels.Run> sorted = new ArrayList<>(runs.values());
        sorted.sort((a, b) -> {
            Instant left = a.createdAt() == null ? Instant.EPOCH : a.createdAt();
            Instant right = b.createdAt() == null ? Instant.EPOCH : b.createdAt();
            return right.compareTo(left);
        });
        return sorted.size() > limit ? sorted.subList(0, limit) : sorted;
    }

    private void trimRuns() {
        if (runs.size() <= MAX_RUNS) {
            return;
        }
        List<DiagnosticModels.Run> sorted = new ArrayList<>(runs.values());
        sorted.sort((a, b) -> {
            Instant left = a.createdAt() == null ? Instant.EPOCH : a.createdAt();
            Instant right = b.createdAt() == null ? Instant.EPOCH : b.createdAt();
            return right.compareTo(left);
        });
        for (int i = MAX_RUNS; i < sorted.size(); i++) {
            runs.remove(sorted.get(i).id());
        }
    }

    // ------------------------------------------------------------------
    // verifications
    // ------------------------------------------------------------------

    public void addVerification(VerificationRecord record) {
        if (record == null) {
            return;
        }
        verifications.add(record);
        while (verifications.size() > MAX_VERIFICATIONS) {
            verifications.remove(0);
        }
        save();
    }

    /** Newest first. */
    public List<VerificationRecord> verifications(int limit) {
        List<VerificationRecord> snapshot = new ArrayList<>(verifications);
        java.util.Collections.reverse(snapshot);
        return snapshot.size() > limit ? snapshot.subList(0, limit) : snapshot;
    }

    // ------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------

    public synchronized void save() {
        if (!writable) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("projects", List.copyOf(projects.values()));
            document.put("runs", runs(MAX_RUNS));
            document.put("verifications", verifications(MAX_VERIFICATIONS));
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document));
        } catch (Exception e) {
            writable = false;
            log.warn("Disabling state persistence ({}): {}", file, e.getMessage());
        }
    }

    public Path file() {
        return file;
    }

    public int runCount() {
        return runs.size();
    }

    public Collection<DiagnosticModels.Run> allRuns() {
        return List.copyOf(runs.values());
    }
}
