package com.stackpilot.backend.health;

import com.stackpilot.backend.health.model.HealthModels;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Active availability checks. Container state alone cannot tell you whether a
 * service answers requests, so every published port is probed for real.
 */
@Service
public class ProbeService {

    private static final int DEFAULT_TIMEOUT_MS = 400;

    public HealthModels.TcpResult tcp(String host, int port, int timeoutMs) {
        if (host == null || host.isBlank() || port <= 0 || port > 65535) {
            return new HealthModels.TcpResult(false, -1, "invalid probe target");
        }
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.max(50, timeoutMs));
            long latency = (System.nanoTime() - started) / 1_000_000L;
            return new HealthModels.TcpResult(true, latency, host + ":" + port + " accepted connection");
        } catch (Exception e) {
            return new HealthModels.TcpResult(false, -1,
                    host + ":" + port + " unreachable (" + describe(e) + ")");
        }
    }

    public HealthModels.TcpResult tcp(String host, int port) {
        return tcp(host, port, DEFAULT_TIMEOUT_MS);
    }

    public boolean reachable(String host, int port) {
        return tcp(host, port).reachable();
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 120) : message;
    }
}
