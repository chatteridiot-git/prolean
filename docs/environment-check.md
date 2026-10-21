# Environment Check — ProLean

- **Date:** 20 October 2026
- **Checked by:** `@devops` (session `ses_ea7d054caffeYKi2hjzr2vsoqu`) with orchestrator completion of the remaining items
- **Status:** **APPROVED** for implementation
- **Project root:** `/mnt/d/Ai/opencode-projects/prolean`

## Toolchain

| Tool | Version | Status | Notes |
|---|---|---|---|
| JDK (Temurin) | 21.0.12.1 LTS | PASS | `java -version`; `JAVA_HOME` resolves to Temurin 21 |
| Maven | 3.9.12 | PASS | `mvn -version`; Java 21 runtime |
| Node.js | 22.23.3 | PASS | `node -v` |
| npm | 10.9.9 | PASS | `npm -v` |
| psql client | 18.6 | PASS | `psql --version`; connects to PostgreSQL 16 |
| PostgreSQL server | 16.15 | PASS (container) | `postgres:16` container; native server binaries are incomplete (see below) |
| Docker CLI | 29.5.3 | PASS | Static Linux CLI installed at `~/.local/bin/docker`, talks to the Docker Desktop engine over `/var/run/docker.sock` |
| Docker Compose | v5.6.0 | PASS | Plugin installed at `~/.docker/cli-plugins/docker-compose`; `docker compose version` |
| Docker engine | 29.5.3 (linux), API 1.54 | PASS | `GET /_ping` over the Unix socket returned 200; `docker run --rm hello-world` succeeded |
| Python | 3.12+ | PASS | Used by the design/review tooling |
| kubectl / Minikube / Kind | not installed | OPTIONAL | The approved design deploys with Docker Compose, not Kubernetes. Install kubectl only if optional manifests are validated later. |
| API gateway | none | N/A | Approved design: no gateway deployed in v1; comparison only. |

## Docker

Docker Desktop is running on Windows. WSL integration is active:

- Unix socket: `/var/run/docker.sock` (root:docker, mode 660); the `kiekie` user is in the `docker` group.
- Engine smoke test: `docker run --rm hello-world` → success.
- Linux CLI: `~/.local/bin/docker` (29.5.3).
- Compose: `~/.docker/cli-plugins/docker-compose` (v5.6.0).
- `DOCKER_HOST` is not required; the default Unix socket is used.

This is the path Testcontainers will use for repository/integration tests. The first Testcontainers-based test in the build must confirm it; if it fails, the fallback is the persistent dev PostgreSQL below, never H2.

If Docker Desktop is not running after a Windows restart:

1. Start **Docker Desktop** and wait for “Engine running”.
2. Ensure **Settings → Resources → WSL Integration** is enabled for this distro.
3. Verify from WSL: `docker version --format 'client={{.Client.Version}} server={{.Server.Version}}'`.

## PostgreSQL

The native PostgreSQL server wrappers in `~/.opencode/bin` are incomplete: `initdb` fails because `/usr/local/pg-build/share/postgresql/postgres.bki` is missing. They are not used.

A persistent development database runs in Docker:

| Item | Value |
|---|---|
| Container | `prolean-dev-postgres` |
| Image | `postgres:16` (PostgreSQL 16.15) |
| Host port | `55432` → container `5432` |
| Superuser | `prolean` / `prolean` (development only) |
| Volume | `prolean-dev-pgdata` |
| Databases | `auth_db` (owner `auth_service`), `user_db` (`user_service`), `task_db` (`task_service`), `timeline_db` (`timeline_service`) |
| Service passwords | same as role name, development only |

Smoke test (all four roles):

```text
auth_db as auth_service
user_db as user_service
task_db as task_service
timeline_db as timeline_service
```

Connection example:

```bash
PGPASSWORD=auth_service psql -h localhost -p 55432 -U auth_service -d auth_db -c 'select 1'
```

Lifecycle:

```bash
docker start prolean-dev-postgres     # after a reboot
docker stop prolean-dev-postgres      # optional
docker logs prolean-dev-postgres      # diagnostics
```

Port `55432` is used deliberately so it cannot conflict with the Compose stack, which publishes PostgreSQL on `5432` when the stack is running. The Compose stack owns its own PostgreSQL container and init scripts.

## Kubernetes

Not required for the approved v1. Docker Compose is the deployment target. Optional Kubernetes manifests, if produced, are documentation only and are not a release gate. No Minikube/Kind cluster is available.

## What remains for coding time

- The first `@SpringBootTest`/repository test using Testcontainers must pass against the Docker socket; this is the definitive Testcontainers smoke test.
- The Compose stack is created during implementation (milestone M5) and must pass its own readiness/smoke checks.
- The development PostgreSQL on `55432` is a convenience/fallback; it is not the Compose database.

## Approval

**APPROVED.** JDK 21, Maven, Node/npm, PostgreSQL 16, Docker 29.5.3, and Docker Compose v5.6.0 are installed and verified. The approved design can be implemented and tested. Kubernetes is intentionally out of scope for v1.

`@coder` may start with milestone M1 in `coding/`.

## Re-check — current workstation, 21 October 2026

The workspace was moved to `/home/kiekie/Documents/workspace/opencode-projects/prolean`. The implementation is complete through milestone M5, so this re-check only re-provisions the toolchain needed to run the release gates on the new host. The approval above remains in force.

| Tool | Version | Status | Notes |
|---|---|---|---|
| JDK | 21.0.12.1 LTS (Ubuntu OpenJDK) | PASS | `/usr/lib/jvm/java-21-openjdk-amd64` |
| Maven | 3.9.11 | PASS | Installed under `~/.local/opt/maven`, linked in `~/.local/bin` |
| Node.js | 22.23.4 | PASS | Installed under `~/.local/opt/node`, linked in `~/.local/bin` |
| npm | 10.9.9 | PASS | |
| Docker engine | 29.5.3 | PASS | Docker Desktop, user service `docker-desktop` |
| Docker Compose | v5.1.4 | PASS | Plugin bundled with Docker Desktop |
| Docker socket | `~/.docker/desktop/docker.sock` | PASS | `DOCKER_HOST` set to this socket for Maven/Testcontainers |
| `jq` / `curl` | installed | PASS | Required by `coding/deploy/smoke.sh` |
| `psql` client | not installed | NOTE | `coding/scripts/db-check.sh` requires it; run it where the client exists or install `postgresql-client` |
| kubectl / Minikube / Kind | not installed | OPTIONAL | Kubernetes remains out of scope for v1 |

Operational notes:

- `export PATH="$HOME/.local/bin:$PATH"` is required in a fresh shell.
- Testcontainers must see `DOCKER_HOST=unix://$HOME/.docker/desktop/docker.sock`; do not set `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` on this host because the socket is not at `/var/run/docker.sock`.
- If Docker Desktop is not running: `systemctl --user start docker-desktop`, then confirm with `docker version`.
- The native PostgreSQL wrappers described above are still not usable; the Compose PostgreSQL service owns the release-gate database.
