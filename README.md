# StackPilot

**Documentation-aware DevOps troubleshooting and guided auto-fixer**

StackPilot is a local-first desktop tool (Kotlin Compose Desktop + Java Spring Boot) that helps developers diagnose Docker-based application failures. It correlates live container logs with project documentation and configuration, explains likely root causes with evidence, suggests fixes, and verifies approved changes.

It currently understands common Docker, Redis, Kafka, and Spring Boot failure patterns.

---

## What it does

- Shows Docker Engine status and the local container list.
- Tails live container logs (polled from the embedded backend).
- Indexes README, Compose, Dockerfile, env, and Spring `application*` files.
- Runs diagnostic rules that cite config keys and log lines.
- Previews file patches, applies them only after approval, then re-checks.

---

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 21 (LTS) |
| Docker Engine or Docker Desktop | 24 or later (optional; config diagnostics work without it) |
| Internet access | Required on first Gradle download |

Gradle is downloaded by the wrapper. You do not need to install it.

---

## Building and running

```bat
.\gradlew.bat build
.\gradlew.bat :desktop-app:run
```

The window starts the Spring Boot backend in the same JVM, waits for `/api/health`, then opens the workspace.

### Windows installer (.exe)

Builds a single self-contained setup executable. It needs no WiX or other packaging tool:

```bat
.\build-installer.ps1
```

Result: `dist\StackPilot-Setup-1.0.0.exe`.

- Installs for all users into `C:\Program Files\StackPilot` (one UAC prompt).
- Adds Start-menu and desktop shortcuts and an entry in **Settings → Apps**.
- Ships its own JVM, so the installed app needs no JDK on the target machine.
- Ships an uninstaller (`Uninstall.exe`, also reachable from **Settings → Apps**).
- The setup executable itself is unsigned, so SmartScreen may warn on first run.

`-SkipBuild` reuses an existing `desktop-build\compose\binaries\main\app` image.

### Linux package (.deb)

jpackage cannot cross-compile, so the Debian bundle has to be built on Linux:

```bash
sudo apt-get install -y fakeroot dpkg-dev   # packaging tools jpackage shells out to
./gradlew :desktop-app:packageDeb
```

Result: `desktop-build/compose/binaries/main/deb/stackpilot_1.0.0_amd64.deb`.

```bash
sudo apt install ./stackpilot_1.0.0_amd64.deb
```

It installs to `/opt/stackpilot`, adds a `stackpilot` launcher on `PATH` and an
entry in the desktop menu. It ships its own JVM, so no JDK is needed on the
target machine.

### Publishing a release (GitHub Actions)

[.github/workflows/release.yml](.github/workflows/release.yml) builds both bundles and
publishes them to the GitHub release for the current app version:

- `build-windows-setup` runs on `windows-latest` and produces the `.exe`.
- `build-linux-deb` runs on `ubuntu-latest` and produces the `.deb`.
- `publish-release` waits for both and attaches every asset to one release.

- Pushing to `main` builds and creates/refreshes the `v<version>` release (the tag is
  created automatically when it does not exist yet).
- Pushing a `v*` tag does the same, and the tag must match the app version.
- A manual run (`workflow_dispatch`) builds and publishes the same way.

1. Set the version in both places (they must match):
   - `desktop-app/build.gradle.kts` → `packageVersion`
   - `installer/Setup.cs` → `AppVersion`
2. Commit and push:

```bat
git add -A
git commit -m "Release v1.0.0"
git push origin main
```

The release gets six assets:

| Asset | Purpose |
|---|---|
| `StackPilot-Setup.exe` | Windows installer, stable name used by the website |
| `StackPilot-Setup-<version>.exe` | Windows installer, versioned |
| `StackPilot-Setup.exe.sha256` | Windows checksum |
| `StackPilot.deb` | Linux Debian package, stable name used by the website |
| `StackPilot-<version>.deb` | Linux Debian package, versioned |
| `StackPilot.deb.sha256` | Linux checksum |

The website links to
`https://github.com/shivammm21/troubleshooting-config/releases/latest/download/StackPilot-Setup.exe`
and
`https://github.com/shivammm21/troubleshooting-config/releases/latest/download/StackPilot.deb`
(see [web/src/config/site.ts](web/src/config/site.ts) and
[web/src/components/DownloadPageClient.tsx](web/src/components/DownloadPageClient.tsx)),
so every new release is served without touching the site.

### Tests

```bat
.\gradlew.bat test
```

### Try a lab without Docker

1. Run the desktop app.
2. Open **Project** and index `labs/redis-hostname-mismatch` (or `labs/kafka-bootstrap-mismatch`).
3. Open **Diagnostics** → **Run diagnostics**.
4. Inspect evidence, **Approve fix**, then **Verify**.

---

## Project structure

```
stackpilot/
├── backend/          Spring Boot library (API, Docker, diagnostics)
├── desktop-app/      Compose Desktop UI and JVM entry point
├── installer/        Windows setup bootstrapper (C# source + manifest)
├── .github/          CI: builds and publishes the Windows setup.exe
├── labs/             Reproducible mismatch samples
├── docs/             Architecture and API
└── README.md
```

---

## Architecture notes

- Single JVM: Compose UI starts Spring Boot with `registerShutdownHook(false)`.
- Backend binds to `127.0.0.1` port `0`. The UI reads the real port after startup.
- API calls (except health) send `X-StackPilot-Token`.
- Docker access uses docker-java against the default engine socket / named pipe.

See [docs/architecture.md](docs/architecture.md) and [docs/api.md](docs/api.md).

---

## Roadmap

| Phase | Description | Status |
|---|---|---|
| 1 | Desktop lifecycle, Spring Boot startup, health check | Done |
| 2 | Docker connectivity, container list | Done |
| 3 | Live log tail | Done |
| 4 | Redis hostname mismatch diagnostic | Done |
| 5 | Documentation / config indexing | Done |
| 6 | Kafka and Spring Boot rules, repair + verify | Done |
| 7 | Optional Spring AI explanations | Planned |
| 8 | Native distribution packaging | Done |

---

## Pinned versions

| Component | Version |
|---|---|
| Kotlin | 2.0.21 |
| Compose Multiplatform | 1.7.3 |
| Spring Boot | 3.3.5 |
| Gradle (wrapper) | 8.10.1 |
| JDK (target) | 21 |
| docker-java | 3.4.1 |
