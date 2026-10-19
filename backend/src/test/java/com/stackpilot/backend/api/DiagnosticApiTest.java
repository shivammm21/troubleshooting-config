package com.stackpilot.backend.api;

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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DiagnosticApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenService tokens;

    @MockBean
    private DockerEngine docker;

    @TempDir
    Path tempDir;

    @BeforeEach
    void dockerDisconnected() {
        when(docker.status()).thenReturn(new DockerModels.Status(false, null, 0, "not running"));
        when(docker.listContainers()).thenReturn(List.of());
    }

    @Test
    void healthRemainsOpen() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void diagnosticsRequireToken() throws Exception {
        mockMvc.perform(post("/api/diagnostics").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void attachDiagnoseRepairAndVerifyRedisMismatch() throws Exception {
        Files.writeString(tempDir.resolve("docker-compose.yml"), """
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

        String pathJson = "{\"path\":\"" + tempDir.toString().replace("\\", "\\\\") + "\"}";
        MvcResult attach = mockMvc.perform(post("/api/projects")
                        .header("X-StackPilot-Token", tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pathJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        String projectId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(attach.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult run = mockMvc.perform(post("/api/diagnostics")
                        .header("X-StackPilot-Token", tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.ruleId=='redis-hostname-mismatch')]").exists())
                .andReturn();

        var tree = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(run.getResponse().getContentAsString());
        String runId = tree.get("id").asText();
        String findingId = null;
        for (var finding : tree.get("findings")) {
            if ("redis-hostname-mismatch".equals(finding.get("ruleId").asText())) {
                findingId = finding.get("id").asText();
            }
        }

        mockMvc.perform(post("/api/diagnostics/" + runId + "/repair-preview")
                        .header("X-StackPilot-Token", tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"findingId\":\"" + findingId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patches[0].replacementSnippet").value("redis"));

        mockMvc.perform(post("/api/diagnostics/" + runId + "/repair-approval")
                        .header("X-StackPilot-Token", tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"findingId\":\"" + findingId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true));

        org.junit.jupiter.api.Assertions.assertTrue(Files.readString(appYml).contains("host: redis"));

        mockMvc.perform(post("/api/diagnostics/" + runId + "/verify")
                        .header("X-StackPilot-Token", tokens.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"findingId\":\"" + findingId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.summary", containsString("no longer detected")));
    }
}
