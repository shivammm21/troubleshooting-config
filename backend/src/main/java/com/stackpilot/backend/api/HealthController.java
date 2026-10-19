package com.stackpilot.backend.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Minimal health endpoint used by the desktop application to confirm that the
 * Spring Boot context has started and is ready to accept requests.
 *
 * GET /api/health
 * Response 200 OK  { "status": "UP", "timestamp": "<ISO-8601>" }
 *
 * Spring Boot Actuator also exposes /actuator/health automatically, but this
 * explicit endpoint is simpler to poll from the desktop without pulling in
 * Actuator's full dependency set, and it lives under our /api prefix so
 * session-token validation can be applied uniformly in a later phase.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}
