# StackPilot – Backend API Contract

All endpoints are served on `http://127.0.0.1:<ephemeral-port>`.

Authenticated routes require header `X-StackPilot-Token` (issued to the desktop process at startup). `GET /api/health` and Actuator remain open so the UI can poll readiness.

## GET /api/health

Returns the backend readiness state. Used by the desktop during startup polling.

**Response 200 OK**
```json
{
  "status": "UP",
  "timestamp": "2024-10-16T12:34:56.789Z"
}
```

## GET /actuator/health

Spring Boot Actuator health endpoint.

## GET /api/docker/status

Docker Engine reachability.

```json
{ "connected": true, "engineName": "docker-desktop", "containerCount": 4, "error": null }
```

## GET /api/containers

All containers (`docker ps -a` equivalent).

## GET /api/containers/{id}/logs?lines=200

Recent stdout/stderr for a container. The desktop polls this endpoint to show a live tail.

## POST /api/projects

Body: `{ "path": "C:/path/to/repo" }`

Indexes README, Compose, Dockerfile, `.env`, and Spring `application*` files. Returns the snapshot (id, files, compose services, Redis/Kafka host bindings).

## GET /api/projects/{id}

## POST /api/projects/{id}/index

Re-reads the same folder in place (id is preserved).

## POST /api/diagnostics

Body: `{ "projectId": "<optional>" }`

Runs Docker, Redis, Kafka, and Spring Boot rules against indexed config plus live logs.

## GET /api/diagnostics/{id}

## POST /api/diagnostics/{id}/repair-preview

Body: `{ "findingId": "..." }` — returns the suggested file patches without writing.

## POST /api/diagnostics/{id}/repair-approval

Applies approved patches. Original files are copied to `*.stackpilot.bak` first.

## POST /api/diagnostics/{id}/verify

Re-indexes the project, re-reads container logs, and reports whether the same rule still fires.

---

## General conventions

- JSON responses; `4xx` bodies are `{ "error": "<message>", "code": "<CODE>" }`.
- `401` when the session token is missing or wrong.
- `400` for malformed input; `404` for unknown ids; `409` when a repair cannot be applied.
