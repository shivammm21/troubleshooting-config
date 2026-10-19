package com.stackpilot.backend;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Boot application root for StackPilot's backend.
 *
 * This class is NOT the main() entry point of the JVM process. The desktop
 * application in the desktop-app module owns the JVM lifecycle and starts this
 * Spring Boot context programmatically via BackendLifecycle (Kotlin).
 *
 * {@code @SpringBootApplication} triggers component scanning of this package
 * and its sub-packages, enabling auto-configuration for Spring Web, Actuator,
 * and any future features added to the backend.
 */
@SpringBootApplication
public class StackPilotBackendApplication {

    private static final Logger log = LoggerFactory.getLogger(StackPilotBackendApplication.class);

    /**
     * Called by the desktop module after the Spring context has started,
     * so the desktop can log the chosen port.
     *
     * @param context the live application context
     * @return the local TCP port that the embedded web server bound to
     */
    public static int resolvePort(ServletWebServerApplicationContext context) {
        int port = context.getWebServer().getPort();
        log.info("StackPilot backend listening on port {}", port);
        return port;
    }
}
