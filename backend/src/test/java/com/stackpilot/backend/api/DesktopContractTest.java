package com.stackpilot.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackpilot.backend.docker.DockerEngine;
import com.stackpilot.backend.docker.model.DockerModels;
import com.stackpilot.backend.security.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks the JSON contract consumed by the desktop client
 * ({@code com.stackpilot.desktop.ApiParsers}).
 *
 * <p>The desktop parses every response defensively, but a silently missing or
 * renamed field would make a feature look "empty" instead of failing loudly.
 * This test walks the whole troubleshooting loop - attach, diagnose, approve,
 * verify, history, report, health - and asserts that each field the UI reads is
 * actually on the wire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DesktopContractTest {

    private static final String TOKEN = "X-StackPilot-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenService tokens;

    @MockBean
    private DockerEngine docker;

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void dockerUnreachable() {
        when(docker.status()).thenReturn(new DockerModels.Status(false, null, 0, "not running"));
        when(docker.listContainers()).thenReturn(List.of());
    }

    @Test
    void everyFieldTheDesktopReadsIsPresentOnTheWire() throws Exception {
        Path compose = tempDir.resolve("docker-compose.yml");
        Files.writeString(compose, """
                services:
                  redis:
                    image: redis:7
                  api:
                    image: app
                """);
        Path appYml = tempDir.resolve("application.yml");
        Files.writeString(appYml, """
                spring:
                  data:
                    redis:
                      host: localhost
                """);
        Files.writeString(tempDir.resolve("README.md"), """
                # Shop

                Run `docker compose up -d`, then open http://localhost:8080.

                The Redis service is reachable at redis:6379 inside the Compose network.
                """);

        // ---- project snapshot (ProjectFullView reads these) -------------
        MvcResult attach = mockMvc.perform(post("/api/projects")
                        .header(TOKEN, tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + tempDir.toString().replace("\\", "\\\\") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.rootPath").isNotEmpty())
                .andExpect(jsonPath("$.files[0].relativePath").isNotEmpty())
                .andExpect(jsonPath("$.files[0].kind").isNotEmpty())
                .andReturn();
        String projectId = mapper.readTree(attach.getResponse().getContentAsString()).path("id").asText();

        // ---- diagnostic run (ApiParsers.findings) ----------------------
        MvcResult runResult = mockMvc.perform(post("/api/diagnostics")
                        .header(TOKEN, tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode run = mapper.readTree(runResult.getResponse().getContentAsString());
        String runId = run.path("id").asText();
        assertFalse(runId.isBlank(), "run id");
        assertTrue(run.path("createdAt").asText().length() >= 19, "run createdAt is an ISO instant");
        assertTrue(run.path("summary").path("total").isNumber(), "run summary.total");

        JsonNode finding = null;
        for (JsonNode candidate : run.path("findings")) {
            if ("redis-hostname-mismatch".equals(candidate.path("ruleId").asText())) {
                finding = candidate;
                break;
            }
        }
        assertNotNull(finding, "redis finding must be present, got: " + run.path("findings"));

        for (String field : List.of("id", "ruleId", "title", "severity", "summary")) {
            assertFalse(finding.path(field).asText().isBlank(), "finding." + field);
        }
        assertEquals(95, finding.path("confidence").asInt(), "finding.confidence");
        assertEquals("configuration", finding.path("category").asText(), "finding.category");
        assertFalse(finding.path("nextSteps").isEmpty(), "finding.nextSteps");
        assertFalse(finding.path("nextSteps").get(0).asText().isBlank(), "finding.nextSteps[0]");

        JsonNode evidence = finding.path("evidence").get(0);
        assertEquals("CONFIG", evidence.path("source").asText(), "evidence.source");
        assertFalse(evidence.path("location").asText().isBlank(), "evidence.location");
        assertFalse(evidence.path("excerpt").asText().isBlank(), "evidence.excerpt");

        JsonNode repair = finding.path("repair");
        assertFalse(repair.isMissingNode() || repair.isNull(), "finding.repair");
        assertFalse(repair.path("description").asText().isBlank(), "repair.description");
        assertEquals("LOW", repair.path("risk").asText(), "repair.risk");
        assertTrue(repair.path("requiresRestart").asBoolean(), "repair.requiresRestart");
        assertTrue(repair.path("actions").isArray(), "repair.actions");
        assertEquals("REPLACE", repair.path("patches").get(0).path("mode").asText(), "patch.mode");
        assertFalse(repair.path("patches").get(0).path("path").asText().isBlank(), "patch.path");
        assertEquals("redis", repair.path("patches").get(0).path("replacementSnippet").asText());

        String findingId = finding.path("id").asText();

        // ---- approved repair without a container restart -----------------
        mockMvc.perform(post("/api/diagnostics/" + runId + "/repair-approval")
                        .header(TOKEN, tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"findingId\":\"" + findingId + "\",\"restart\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.changedFiles[0]").isNotEmpty())
                .andExpect(jsonPath("$.backups[0]").isNotEmpty())
                .andExpect(jsonPath("$.restartedContainers").isArray())
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertTrue(Files.readString(appYml).contains("host: redis"), "patch was written to disk");

        // ---- verification verdict (VerificationOutcome) ------------------
        MvcResult verifyResult = mockMvc.perform(post("/api/diagnostics/" + runId + "/verify")
                        .header(TOKEN, tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"findingId\":\"" + findingId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.findingId").value(findingId))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.runtimeChecks").isArray())
                .andReturn();
        JsonNode verdict = mapper.readTree(verifyResult.getResponse().getContentAsString());
        assertFalse(verdict.path("status").asText().isBlank(), "verification.status");
        assertTrue(verdict.path("checkedAt").asText().length() >= 19, "verification.checkedAt");

        // ---- persisted history (HistoryPanel) ---------------------------
        MvcResult history = mockMvc.perform(get("/api/diagnostics?limit=10")
                        .header(TOKEN, tokens.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode runs = mapper.readTree(history.getResponse().getContentAsString());
        assertTrue(runs.isArray() && !runs.isEmpty(), "history is a non-empty array");
        JsonNode firstRun = runs.get(0);
        assertFalse(firstRun.path("id").asText().isBlank(), "runRef.id");
        assertTrue(firstRun.path("createdAt").asText().length() >= 19, "runRef.createdAt");
        assertTrue(firstRun.path("findings").isNumber(), "runRef.findings");
        assertTrue(firstRun.path("errors").isNumber(), "runRef.errors");
        assertTrue(firstRun.path("repairable").isNumber(), "runRef.repairable");

        MvcResult records = mockMvc.perform(get("/api/verifications?limit=10")
                        .header(TOKEN, tokens.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode verificationHistory = mapper.readTree(records.getResponse().getContentAsString());
        assertTrue(verificationHistory.isArray() && !verificationHistory.isEmpty(),
                "verification history is a non-empty array");
        JsonNode record = verificationHistory.get(0);
        for (String field : List.of("id", "at", "runId", "ruleId", "status", "summary")) {
            assertFalse(record.path(field).asText().isBlank(), "verificationRecord." + field);
        }
        assertTrue(record.path("resolved").isBoolean(), "verificationRecord.resolved");

        // ---- shareable Markdown report ----------------------------------
        MvcResult report = mockMvc.perform(get("/api/diagnostics/" + runId + "/report")
                        .header(TOKEN, tokens.token()))
                .andExpect(status().isOk())
                .andReturn();
        String markdown = report.getResponse().getContentAsString();
        assertTrue(markdown.contains("Root cause analysis"), "report has a root-cause section");
        assertTrue(markdown.contains("Post-fix verification"), "report has a verification section");
        assertTrue(markdown.contains("redis-hostname-mismatch"), "report cites the rule");

        // ---- infrastructure health (HealthPanel) ------------------------
        MvcResult healthResult = mockMvc.perform(get("/api/infra/health")
                        .header(TOKEN, tokens.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode health = mapper.readTree(healthResult.getResponse().getContentAsString());
        assertFalse(health.path("overall").asText().isBlank(), "health.overall");
        assertFalse(health.path("dockerConnected").asBoolean(true), "health.dockerConnected is false when Docker is down");
        assertFalse(health.path("engineName").asText().isBlank(), "health.engineName");
        assertTrue(health.path("totalContainers").isNumber(), "health.totalContainers");
        assertTrue(health.path("runningContainers").isNumber(), "health.runningContainers");
        assertTrue(health.path("unhealthyContainers").isNumber(), "health.unhealthyContainers");
        assertTrue(health.path("stoppedContainers").isNumber(), "health.stoppedContainers");
        assertTrue(health.path("containers").isArray(), "health.containers");
        assertTrue(health.path("dependencies").isArray(), "health.dependencies");
        assertTrue(health.path("issues").isArray(), "health.issues");
        assertFalse(health.path("checkedAt").asText().isBlank(), "health.checkedAt");

        // ---- rollback leaves the file exactly as it was -----------------
        mockMvc.perform(post("/api/diagnostics/" + runId + "/repair-rollback")
                        .header(TOKEN, tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"findingId\":\"" + findingId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true));
        assertTrue(Files.readString(appYml).contains("host: localhost"), "rollback restored the file");
    }

    @Test
    void repairWithoutAFindingIdIsRejectedWithAnErrorMessage() throws Exception {
        mockMvc.perform(post("/api/diagnostics/any-run/repair-approval")
                        .header(TOKEN, tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unknownRunAndUnknownContainerAreReportedAsNotFound() throws Exception {
        mockMvc.perform(get("/api/diagnostics/does-not-exist")
                        .header(TOKEN, tokens.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // With the engine reachable but the container absent, resolving the reference
        // must fail loudly so the UI can say "container not found" instead of
        // showing an empty log panel.
        when(docker.status()).thenReturn(new DockerModels.Status(true, "engine", 1, null));
        when(docker.listContainers()).thenReturn(List.of());

        mockMvc.perform(get("/api/containers/does-not-exist/log-analysis")
                        .header(TOKEN, tokens.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
