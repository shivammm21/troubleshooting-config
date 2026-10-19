# StackPilot – Architecture

## Single-Process Model

```
┌──────────────────────────────────────────────────────────┐
│  JVM Process (launched by the user)                      │
│                                                          │
│  ┌────────────────────────┐  ┌─────────────────────────┐ │
│  │  Kotlin Compose UI     │  │  Spring Boot Context    │ │
│  │                        │  │                         │ │
│  │  BackendLifecycle  ────┼──►  starts / stops         │ │
│  │  BackendClient         │  │  /api/health            │ │
│  │  (token + HTTP)        │  │  /api/docker/*          │ │
│  │                        │  │  /api/projects          │ │
│  │                        │  │  /api/diagnostics       │ │
│  └────────────────────────┘  └─────────────────────────┘ │
│                                                          │
│  Loopback only – port 0 (ephemeral)                      │
└──────────────────────────────────────────────────────────┘
                      │
                      │  Docker Engine API (Unix socket / npipe)
                      ▼
              Docker Engine / Docker Desktop
```

## Troubleshooting loop

1. **Observe** – list containers and tail logs from the Docker Engine.
2. **Index** – parse project README, Compose, and Spring config.
3. **Diagnose** – rules compare live logs with documented topology (Redis, Kafka, Spring Boot, Docker health).
4. **Explain** – each finding carries evidence (config path + log excerpt).
5. **Fix** – optional file patches are previewed, then applied only after approval.
6. **Verify** – re-index and re-run the same rule to see if the issue is gone.

## Startup Sequence

```
Main.kt
  └─ LaunchedEffect(Unit)
       └─ appScope.launch(IO)
            ├─ BackendLifecycle.start()
            │     └─ SpringApplicationBuilder.run()
            ├─ read SessionTokenService.token()
            ├─ BackendLifecycle.waitUntilReady()  →  GET /api/health
            └─ AppState.Ready(port, baseUrl, token)
```

## Shutdown Sequence

Window close → `BackendLifecycle.stop()` → Spring context close → `exitApplication()`.

## Module Dependency Graph

```
desktop-app  ──depends on──►  backend
desktop-app  ──depends on──►  compose desktop, ktor (legacy), Jackson, Spring Web
backend      ──depends on──►  spring-boot-starter-web, actuator, docker-java, jackson-yaml
```

## Security Model

- Embedded Tomcat bound to `127.0.0.1` with an ephemeral port.
- Sensitive routes require `X-StackPilot-Token` generated in-memory at startup.
- Repairs write only under the indexed project root and keep `*.stackpilot.bak` copies.
- No credentials are persisted.
