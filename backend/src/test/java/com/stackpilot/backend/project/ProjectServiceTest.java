package com.stackpilot.backend.project;

import com.stackpilot.backend.project.model.ProjectModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void indexesComposeAndSpringRedisHost() throws Exception {
        Files.writeString(tempDir.resolve("docker-compose.yml"), """
                services:
                  redis:
                    image: redis:7
                  api:
                    image: app
                """);
        Files.writeString(tempDir.resolve("application.yml"), """
                spring:
                  data:
                    redis:
                      host: localhost
                """);
        Files.writeString(tempDir.resolve("README.md"), "Redis service is named redis.");

        ProjectModels.Snapshot snapshot = new ProjectService().attach(tempDir.toString());
        assertTrue(snapshot.composeServices().contains("redis"));
        assertTrue(snapshot.composeServices().contains("api"));
        assertEquals(1, snapshot.redisHosts().size());
        assertEquals("localhost", snapshot.redisHosts().getFirst().value());
    }
}
