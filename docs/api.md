# StackPilot – Backend API Contract

All endpoints are served on `http://127.0.0.1:<ephemeral-port>` from inside the desktop process.

Authenticated routes require header `X-StackPilot-Token` (issued to the desktop process at startup). `GET /api/health` and Actuator remain open so the UI can poll readiness.

Every endpoint below is exercised end-to-end by `DesktopContractTest`, which asserts the exact
field names the desktop parser (`com.stackpilot.desktop.ApiParsers`) reads.

| Feature | Endpoints |
| --- | --- |
| 1 – Infrastructure health monitoring | `GET /api/infra/health`, `GET /api/infra/containers`, `GET /api/docker/status`, `GET /api/containers` |
| 2 – Log analysis and error detection | `GET /api/containers/{id}/logs`, `GET /api/containers/{id}/log-analysis` |
| 3 – Root-cause analysis | `POST /api/diagnostics` (ranked findings with evidence) |
| 4 – Documentation-aware diagnostics | covered by `POST /api/projects` indexing + `DOC` evidence |
| 5 – Smart fix recommendations with approval | `POST /api/diagnostics/{id}/repair-preview`, `POST /api/diagnostics/{id}/repair-approval`, `POST /api/diagnostics/{id}/repair-rollback` |
| 6 – Post-fix verification | `POST /api/diagnostics/{id}/verify`, `GET /api/verifications`, `GET /api/diagnostics/{id}/report` |

## GET /api/health

Backend readiness. Used by the desktop during startup polling.

```json
{ "status": "UP", "timestamp": "2024-10-16T12:34:56.789Z" }
```

## GET /actuator/health

Spring Boot Actuator health endpoint.

## GET /api/infra/health

Feature 1. One call returns the engine state, every container's health and a live TCP probe of
each published host port.

```json
{
  "overall": "DEGRADED",
  "dockerConnected": true,
  "engineName": "docker-desktop",
  "dockerError": null,
  "totalContainers": 3,
  "runningContainers": 2,
  "unhealthyContainers": 1,
  "stoppedContainers": 1,
  "containers": [
    { "id": "abc123", "name": "shop-api", "state": "running", "status": "Up 2 minutes (unhealthy)",
      "health": "unhealthy", "ports": ["8080:8080"], "exitCode": null, "restartCount": 1 }
  ],
  "dependencies": [
    { "service": "redis", "container": "redis-1", "containerState": "running", "health": "healthy",
      "hostPort": 6379, "containerPort": 6379, "reachable": true, "latencyMs": 3,
      "detail": "redis reachable on 127.0.0.1:6379 in 3ms" }
  ],
  "issues": ["shop-api is unhealthy"],
  "checkedAt": "2026-10-29T09:15:03.123Z"
}
```

`overall` is `UP`, `DEGRADED` or `DOWN`. When Docker is unreachable the field is `DOWN`,
`dockerConnected` is `false` and `dockerError` explains why.

## GET /api/infra/containers

Same container list, broken containers first.

## GET /api/docker/status

```json
{ "connected": true, "engineName": "docker-desktop", "containerCount": 4, "error": null }
```

## GET /api/containers

All containers (`docker ps -a` equivalent).

## GET /api/containers/{id}/logs?lines=200

Recent stdout/stderr. The desktop polls this endpoint for the live tail. `id` accepts a full id,
a short id or a container name.

## GET /api/containers/{id}/log-analysis?lines=300

Feature 2. Raw tail plus grouped failure signatures, so the UI can show `12x connection refused`
instead of a wall of text.

```json
{
  "containerId": "abc123",
  "containerName": "shop-api",
  "requestedLines": 300,
  "raw": "…",
  "summary": {
    "totalLines": 300, "errorLines": 41, "warnLines": 3, "infoLines": 256, "stackTraceLines": 12,
    "signatures": [
      { "signature": "java.net.ConnectException: Connection refused", "category": "CONNECTION",
        "severity": "ERROR", "count": 12, "sample": "…", "line": 17 }
    ],
    "exceptionTypes": ["java.net.ConnectException"]
  }
}
```

## POST /api/containers/{id}/restart

## POST /api/containers/{id}/start

Explicit, user-initiated container actions from the health panel.

```json
{ "containerId": "abc123", "ok": true, "message": "Restart requested. Give the container a few seconds to become healthy." }
```

## POST /api/projects

Body: `{ "path": "C:/path/to/repo" }`

Indexes README, Compose, Dockerfile, `.env` and Spring `application*` files. Returns the snapshot
(id, files, compose services, Redis/Kafka/database bindings, doc facts).

## GET /api/projects

All attached projects (in-memory plus persisted).

## GET /api/projects/{id}

## GET /api/projects/{id}/view

Snapshot view of one project (root path, file list, bindings, documentation facts).

## POST /api/projects/{id}/index

Re-reads the same folder in place (id is preserved).

## POST /api/diagnostics

Body: `{ "projectId": "<optional>" }`

Feature 3. Runs every rule against indexed configuration, documentation and live container logs,
correlates duplicates and returns ranked findings.

```json
{
  "id": "5f0c…",
  "createdAt": "2026-10-29T09:15:03.123Z",
  "projectId": "0a1b…",
  "summary": { "total": 1, "errors": 1, "warnings": 0, "repairable": 1, "services": ["redis"] },
  "logHealth": { "containers": 2, "totalLines": 600, "errorLines": 41, "topSignatures": ["…"] },
  "findings": [
    {
      "id": "b7e2…",
      "ruleId": "redis-hostname-mismatch",
      "title": "Redis hostname does not match the Compose service",
      "severity": "ERROR",
      "summary": "Compose defines a Redis service named `redis`, but application.yml configures spring.data.redis.host = localhost …",
      "confidence": 95,
      "category": "configuration",
      "rootCauseKey": "redis-host|application.yml|spring.data.redis.host",
      "nextSteps": ["Apply the patch to application.yml", "Restart the app container", "Re-run diagnostics"],
      "evidence": [
        { "source": "CONFIG", "location": "application.yml → spring.data.redis.host", "excerpt": "localhost" },
        { "source": "LOG", "location": "shop-api", "excerpt": "RedisConnectionException: Unable to connect to localhost:6379" },
        { "source": "DOC", "location": "README.md:7", "excerpt": "The Redis service is reachable at redis:6379 …" }
      ],
      "repair": {
        "description": "Point the Redis client at the Compose service name `redis` …",
        "risk": "LOW",
        "requiresRestart": true,
        "patches": [
          { "path": "application.yml", "originalSnippet": "localhost", "replacementSnippet": "redis", "mode": "REPLACE" }
        ],
        "actions": []
      }
    }
  ]
}
```

`evidence.source` is one of `CONFIG`, `LOG`, `DOCKER`, `PROBE`, `DOC`; every location is citable
(`file → key` or `file:line`). `repair` is `null` when no automated fix exists and `nextSteps`
carries the manual procedure instead.

Patch `mode` is `REPLACE`, `APPEND_LINE` or `CREATE_FILE`. Repair actions are
`RESTART_CONTAINER` or `START_CONTAINER`.

## GET /api/diagnostics/{id}

One stored run by id.

## GET /api/diagnostics?limit=20

`[{ "id": "…", "createdAt": "…", "projectId": "…", "findings": 1, "errors": 1, "repairable": 1 }]`

Newest first; persisted in `~/.stackpilot/state.json` (override the folder with
`STACKPILOT_STATE_DIR`), so history survives app restarts.

## POST /api/diagnostics/{id}/repair-preview

Body: `{ "findingId": "..." }` — returns the suggested patches/actions without writing.

## POST /api/diagnostics/{id}/repair-approval

Body: `{ "findingId": "...", "restart": true }`

Feature 5. Applies the approved patches and container actions. Original files are copied to
`*.stackpilot.bak` first. `restart` also restarts the affected containers so the new value is
actually loaded.

```json
{ "findingId": "b7e2…", "applied": true, "changedFiles": ["application.yml"],
  "backups": ["application.yml.stackpilot.bak"], "restartedContainers": ["shop-api"],
  "message": "Updated application.yml. Restarted: shop-api. Verification runs automatically." }
```

## POST /api/diagnostics/{id}/repair-rollback

Body: `{ "findingId": "..." }` — restores every `.stackpilot.bak` written for that finding.

## POST /api/diagnostics/{id}/verify

Feature 6. Two-layer verification: re-indexes the project and re-runs every rule, then checks the
runtime (container state + live TCP probes, with a short settle window after a restart).

```json
{
  "runId": "5f0c…", "findingId": "b7e2…",
  "resolved": false,
  "status": "CONFIG_FIXED_RUNTIME_PENDING",
  "summary": "The configuration is fixed, but the runtime is not healthy yet (shop-api is exited). Restart or start the affected container and verify again.",
  "remaining": [],
  "runtimeChecks": ["Container `shop-api`: state exited, health none", "Reachable: `redis` on 127.0.0.1:6379 (3ms)"],
  "checkedAt": "2026-10-29T09:16:41.007Z"
}
```

`status` is one of:

- `RESOLVED` – the rule no longer fires and the runtime is healthy.
- `CONFIG_FIXED_RUNTIME_PENDING` – the file is fixed but the running stack has not picked it up yet.
- `UNRESOLVED` – the problem is still detected.
- `NO_FIX` – nothing was ever auto-fixable; the manual steps still apply.

## GET /api/verifications?limit=20

`[{ "id": "…", "at": "…", "projectId": "…", "runId": "…", "findingId": "…",
     "ruleId": "redis-hostname-mismatch", "resolved": false, "status": "…", "summary": "…" }]`

## GET /api/diagnostics/{id}/report

Returns `text/markdown`: metadata, infrastructure health, root-cause analysis with evidence and
suggested patches, and the post-fix verification table. Saved by the desktop as
`stackpilot-report-<run>.md`.

---

## General conventions

- JSON responses; `4xx` bodies are `{ "error": "<message>", "code": "<CODE>" }`.
- `401` when the session token is missing or wrong.
- `400` for malformed input; `404` for unknown ids; `409` when a repair cannot be applied.
