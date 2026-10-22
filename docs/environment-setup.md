# ProLean Development Environment Setup

**Date:** 2026-10-19
**Host:** Windows 11 + WSL2
**Distro:** Ubuntu 26.04.1 LTS (Resolute Raccoon), x86_64
**Linux user:** `kiekie`
**Design reference:** `docs/ProLean_Technical_Design_Document.docx`

This document records the toolchain installed to build, run, and test the ProLean
microservices and frontend. Everything on the Linux side is installed in user space
under `~/.local/opt`; no `sudo`/root was required. Docker is provided by the existing
Docker Desktop installation on Windows.

---

## 1. Summary

| Area | Tool | Version | Status |
|---|---|---|---|
| Language | Eclipse Temurin JDK | 21.0.12.1+1 (LTS) | Installed and verified |
| Build | Apache Maven | 3.9.12 | Installed and verified |
| Runtime (FE) | Node.js | 22.23.3 (LTS) | Installed and verified |
| Runtime (FE) | npm | 10.9.9 | Installed and verified |
| Database server | PostgreSQL 16 | 16.15 (Docker image `postgres:16`) | Image pulled and verified |
| Database client | psql / pg_dump / pg_restore | 18.6 (Ubuntu package) | Installed and verified |
| Containers | Docker Engine (Docker Desktop) | 29.5.3, Linux engine | WSL integration enabled and verified |
| Containers | Docker Compose | v5.1.4 | Installed and verified |
| CLI helpers | jq / unzip / zip | 1.8.1 / 6.00 / 3.0 | Installed and verified |
| SCM | git | 2.53.0 | Already present |

---

## 2. Install locations

| Tool | Location |
|---|---|
| JDK 21 (Temurin) | `~/.local/opt/temurin-21` |
| Maven 3.9.12 | `~/.local/opt/apache-maven-3.9.12` |
| Node.js 22 | `~/.local/opt/node` |
| PostgreSQL client 18.6 | `~/.local/opt/pg-client` |
| PostgreSQL server 16.15 (embedded, unused) | `~/.local/opt/postgresql-16` |
| jq / unzip / zip | `~/.local/opt/tools` |
| Maven local repository | `~/.m2/repository` |
| npm global prefix | `/home/kiekie/.local` (set in `~/.npmrc`) |

---

## 3. Shell integration

The file `~/.config/prolean/env.sh` exports `JAVA_HOME`, `MAVEN_HOME`, `NODE_HOME`,
prepends the tool directories to `PATH`, and adds the bundled `libpq` and `libjq`
directories to `LD_LIBRARY_PATH`. It is sourced automatically from `~/.bashrc` and
`~/.profile`, so every new login shell has the toolchain available.

Wrapper scripts (to set `JAVA_HOME` or `LD_LIBRARY_PATH` correctly) are placed in:

- `~/.local/bin` — used by normal login shells (this directory is on `PATH`).
- `~/.opencode/bin` — used by the OpenCode agent shell.

To load the toolchain manually in the current shell:

```bash
source ~/.config/prolean/env.sh
```

---

## 4. Docker Desktop WSL integration

Docker Desktop 4.78.0 was already installed on Windows, but the Ubuntu distro was
not integrated. Integration was enabled by adding the following keys to
`C:\Users\Henki\AppData\Roaming\Docker\settings-store.json` and restarting
Docker Desktop:

```json
{
  "EnableIntegrationWithDefaultWslDistro": true,
  "IntegratedWslDistros": ["Ubuntu"],
  "WslEnableGrpcfuse": false,
  "wslEngineEnabled": true
}
```

- A backup of the original file was saved next to it as
  `settings-store.json.prolean-backup`.
- After the restart, the distro has `/usr/bin/docker` and `/usr/bin/docker-compose`
  symlinks and the socket `/var/run/docker.sock`.
- Docker Desktop added the Linux user `kiekie` to the `docker` group
  (`docker:x:1001:kiekie`).

> **Important:** the currently running OpenCode process was started before the
> `docker` group change, so it does not yet have Docker socket permission.
> Restart OpenCode (or the WSL distro) once to pick up the new group. Fresh WSL
> sessions already work, which was verified with
> `wsl.exe -d Ubuntu -- docker info`.

---

## 5. Database

The design uses a single PostgreSQL 16 instance with four databases
(`auth_db`, `user_db`, `task_db`, `timeline_db`), created by Docker Compose during
implementation.

- The Docker image `postgres:16` (currently 16.15) was pulled and verified.
- A throwaway container on port `55432` was started and queried successfully with
  the local `psql` 18.6 client; the container was then removed.
- The Zonky embedded PostgreSQL 16.15 server binaries in
  `~/.local/opt/postgresql-16` are available as a fallback but are **not** used;
  Docker is the source of truth.
- Existing unrelated containers on the machine (`postgre`, `Apache-Kafka`) were
  left untouched.

Connection example for local development (after `docker compose up -d`):

```bash
psql "postgresql://prolean:<password>@127.0.0.1:5432/prolean_dev"
```

---

## 6. Verification (smoke tests)

### Backend — Spring Boot 3.5.16 + Testcontainers 1.21.4

A throwaway project in `/tmp/opencode/smoke` was built with the installed Maven and
JDK. The test connected to Docker through `/var/run/docker.sock`, started a
`postgres:16` Testcontainer, and ran `select 1` over JDBC:

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This confirms Maven Central access, Spring Boot dependency resolution, JDK 21
compilation, Docker socket access, Testcontainers, and PostgreSQL 16 connectivity.

### Frontend — Vite + React + TypeScript

A throwaway project was created with `npm create vite@latest -- --template react-ts`,
installed with `npm install`, and built with `npm run build`:

```
✓ built in 197ms
```

This confirms npm registry access and a working React + TypeScript + Vite build.
The smoke project used the latest Vite 8.3.4; the implementation will pin the
design-approved Vite 7 line.

---

## 7. Versions to use in `coding/`

| Component | Version |
|---|---|
| Java | 21 (Temurin 21.0.12.1) |
| Spring Boot | 3.5.x (3.5.16 verified) |
| Maven | 3.9.12 |
| PostgreSQL | 16 (`postgres:16` Docker image) |
| Testcontainers | 1.21.4 (managed by the Spring Boot BOM) |
| Node.js | 22.23.3 |
| npm | 10.9.9 |
| React / React DOM | 19.1.10 |
| TypeScript | 5.9.3 |
| Vite | 7.3.7 |
| Ant Design | 5.29.3 |

---

## 8. Notes

- `dlcdn.apache.org` was unreachable from this network; Maven was downloaded from
  Maven Central (`repo1.maven.org`) instead. All other downloads used their
  official sources (Adoptium, nodejs.org, Ubuntu archive).
- To revert the Docker Desktop integration, restore
  `settings-store.json.prolean-backup`, restart Docker Desktop, and remove the
  distro's docker symlinks.
- The `coding/` folder remains empty. Implementation starts only after the
  technical design document is approved.
