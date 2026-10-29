package com.stackpilot.backend.health;

import com.stackpilot.backend.health.model.HealthModels;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeServiceTest {

    private final ProbeService probes = new ProbeService();

    @Test
    void detectsAnOpenPortAndRefusesAClosedOne() throws Exception {
        int port;
        try (ServerSocket server = new ServerSocket(0)) {
            port = server.getLocalPort();

            HealthModels.TcpResult open = probes.tcp("127.0.0.1", port, 1000);
            assertTrue(open.reachable(), open.detail());
            assertTrue(open.latencyMs() >= 0, "latency must be recorded for a successful probe");
        }

        // The listener is closed now, so the same probe must report the port as down.
        HealthModels.TcpResult closed = probes.tcp("127.0.0.1", port, 300);
        assertFalse(closed.reachable(), "a closed listening socket must not look reachable");
    }

    @Test
    void rejectsInvalidTargets() {
        assertFalse(probes.tcp("", 8080).reachable());
        assertFalse(probes.tcp("127.0.0.1", 0).reachable());
        assertFalse(probes.tcp("127.0.0.1", 70_000).reachable());
    }
}
