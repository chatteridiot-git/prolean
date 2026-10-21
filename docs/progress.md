# ProLean — Progress and Resume Checkpoint

**Status:** Implementation complete; final code re-review **APPROVED** with no blocking findings.
**Checkpoint date:** 21 October 2026
**Working directory:** `/mnt/d/Ai/opencode-projects/prolean`

This file is the handoff point for resuming work after a shutdown. Read it first, then
`docs/design.md`, `docs/implementation-plan.md`, and `docs/coder-sessions.md`.

---

## 1. Current machine / repository state

- Docker Compose stack is **stopped and removed**, including its volumes and network
  (`docker compose ... down -v`).
- The separate development PostgreSQL container `prolean-dev-postgres` is **exited (0)**.
- No `deploy-*` or `prolean-*` Compose containers are running.
- Git repository `main` contains the initial commit plus the ProLean implementation commit;
  inspect with `git log --oneline -3`. The working tree should be clean.
- `coding/frontend/coverage/` is a generated artifact and is ignored via `.gitignore`.
- Generated `target/`, `dist/`, `node_modules/`, `coverage/`, `.env`, and logs are ignored.
- The initial commit message contains the original typo `Intial commit`; it is harmless.

---

## 2. Objective and delivered scope

ProLean is a project-management application with tasks and versioned timelines.

Delivered:

- Exactly four deployable Spring Boot services plus the non-deployable `common` library:
  - `auth-service` — port 8081, `auth_db`
  - `user-service` — port 8082, `user_db`
  - `task-service` — port 8083, `task_db`
  - `timeline-service` — port 8084, `timeline_db`
- One PostgreSQL instance with four isolated databases and one role per service.
- Responsive React 19 + TypeScript + Vite + Ant Design frontend.
- Docker Compose deployment with Nginx same-origin API routing and health-gated startup.
- Flyway migrations, Indonesian holiday seed for 2025–2027 (78 entries).
- Unit, slice, repository/Testcontainers, WireMock, concurrency, page-flow, and smoke tests.
- Documentation: design, implementation plan, environment checks, session evidence, diagrams,
  API descriptions, and production-hardening notes.

No API gateway is deployed in v1; this is an approved deviation. Kubernetes remains optional and
is not a release gate.

---

## 3. Key implemented behavior (do not regress)

### Auth

- RS256 access JWT (15 min) with `kid`; JWKS endpoint.
- Rotating HttpOnly refresh cookie, token families, reuse detection, revocation.
- Login lockout: 5 failures → 15-minute lock → `AUTH_ACCOUNT_LOCKED` (423).
- Credentials and password hashes exist only in `auth-service`/`auth_db`.
- User profile creation is followed by an admin password set through auth endpoint A5.
- Internal APIs require `X-Internal-Key-Id` + `X-Internal-Api-Key`, scoped I1–I5.
- Internal keys must be at least 32 characters; startup validation rejects short secrets.
- `AUTH_COOKIE_SECURE` defaults false for local HTTP; production must set it true.
- Refresh failures clear the refresh cookie.

### Users/projects

- Users see only projects they belong to; ADMIN sees all projects and manages membership.
- Deactivated users are denied internal project access.
- Members are sorted with `addedAt,asc`.
- Last-active-admin protection and self-deactivation guard are implemented.
- Users are deactivated; projects are archived; tasks are hard-deleted.

### Tasks

- Inclusive calendar-day duration between start and end dates.
- PIC must be an active project member; user ID and name snapshot are stored.
- Statuses: `OPEN`, `IN_PROGRESS`, `CLOSED`; `CLOSED` requires 100%.
- Idempotent create via `requestId`; replay returns 200 with `Idempotent-Replay: true`.

### Timeline

- One identity per project; versioned revisions are mandatory.
- At most one DRAFT and one PUBLISHED version.
- Draft stores planned dates and notes only; publish carries forward operational status/actual %
  and snapshots the outgoing version.
- Published versions are never deleted. Cancelling the only draft of a never-published timeline
  deletes the identity in the same transaction.
- Working-day duration excludes weekends and Indonesian holidays.
- Zero working days: read returns `durationWorkdays=0`, `percentPlan=0.00`,
  `planWarning=NO_WORKING_DAYS`; create/edit rejects `NO_WORKING_DAYS`; publish is allowed.
- Holiday coverage horizon is whole calendar years derived from the holiday table:
  **2025-01-01 through 2027-12-31**. Read headers and write validation both use
  `HolidayService.calendarHorizon(...)`, so they cannot diverge.
- Timeline lifecycle writes take the timeline row lock. Timestamp mismatch returns
  `409 DRAFT_MODIFIED` or `409 CURRENT_VERSION_CHANGED`.
- All timeline status/progress/cancel mutation paths re-check project access before commit.
- Cross-tab conflict recovery supports reload-and-reapply, discard, pending guard, and inline error.

### API conventions

- RFC 7807 `application/problem+json`.
- List envelope: `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.
- `X-Holiday-Coverage-Start` / `X-Holiday-Coverage-End` are exposed through CORS.

---

## 4. Final verification evidence

Captured before the Compose stack was torn down. Full details are in
`docs/coder-sessions.md` under “Verification evidence (21 October 2026)”.

| Gate | Result |
|---|---|
| Backend | `cd coding/backend && mvn -B clean verify` → **BUILD SUCCESS** |
| Backend tests | **216**: common 34, auth 53, user 48, task 25, timeline 56 |
| JaCoCo | All module coverage checks met (100% lines on gated business packages) |
| PIT | `WorkingDayCalculator`: 17 mutations, 16 killed, **94%** |
| Frontend | **84** tests / 14 files |
| Frontend coverage | Statements **100%**, branches **94.96%**, functions **100%**, lines **100%** |
| Frontend lint | **0 errors**, 2 fast-refresh warnings |
| Frontend build | **success** |
| Compose | PostgreSQL + all four services + frontend all **healthy** |
| Smoke | `coding/deploy/smoke.sh` → **7/7** steps passed |
| DB isolation | `coding/scripts/db-check.sh` → all cross-database checks passed |
| Re-review | **APPROVED**, no blocking findings |

Latest verified smoke identifiers are intentionally not required for resumption; the smoke script
creates new data on each run.

### Test coverage scopes / accepted residual items

- PIT is intentionally scoped to `WorkingDayCalculator`. Expanding it to the whole
  `TimelineService` produced 67% and would require a refactor; the documented gate passes for the
  calculator.
- Frontend coverage gates are scoped to `features`, `auth`, `lib`, and `components` per design
  Table 69. `pages` and drawer composition are exercised by page/component flows but are not part
  of the line-coverage gate.
- OpenAPI is generated and asserted by integration tests in all four services. In Compose,
  `OPENAPI_ENABLED` defaults to `false`; `/v3/api-docs` is therefore not exposed there.
- Compose credentials, `AUTH_COOKIE_SECURE=false`, and OpenAPI defaults are **development
  defaults** and approved for local use. Production overrides are documented in `coding/README.md`.
- Vite reports a large-chunk warning and lint reports two fast-refresh warnings; neither is a gate
  failure.

---

## 5. Resume commands

### 5.1 Inspect state first

```bash
cd /mnt/d/Ai/opencode-projects/prolean
git status --short --branch
docker ps -a --format '{{.Names}}\t{{.Status}}' | grep -E 'deploy-|prolean-' || true
```

Do not discard the uncommitted working tree.

### 5.2 Backend verification

```bash
cd coding/backend
find . -name jacoco.exec -delete
mvn -B clean verify
```

Use `-pl <module> -am` for a single module, because `common` is not installed into the local Maven
repository.

### 5.3 Frontend verification

```bash
cd coding/frontend
npm install --legacy-peer-deps
npx tsc -p tsconfig.json --noEmit
npm run lint
npx vitest run --coverage
npm run build
```

The full frontend coverage run takes roughly **16 minutes** in this WSL environment; run it alone.
Docker builds use `npm ci --legacy-peer-deps` (`coding/frontend/Dockerfile`).

### 5.4 Start and verify the full stack

```bash
cd coding/backend
mvn -B -DskipTests package

cd ../deploy
cp -n .env.example .env
docker compose up -d --build
docker compose ps

# Wait for all health checks to report healthy, then:
./smoke.sh
cd ../scripts
./db-check.sh
```

Frontend: <http://localhost:8080>. Service ports: 8081–8084. PostgreSQL: 5432.

### 5.5 Stop the stack

```bash
docker compose -f coding/deploy/docker-compose.yml down -v
```

The Compose stack was already stopped with this command at checkpoint time.

### 5.6 Run services without Compose (optional)

Start the dev PostgreSQL container on port 55432:

```bash
docker start prolean-dev-postgres
cd coding/backend
mvn -B -pl auth-service spring-boot:run
# repeat with user-service, task-service, timeline-service in separate terminals
```

---

## 6. Important files

| Path | Purpose |
|---|---|
| `docs/design.md` | Approved design v1.1 (authoritative text mirror) |
| `docs/implementation-plan.md` | Milestones and definition of done |
| `docs/environment-check.md` | Environment gate and prerequisites |
| `docs/coder-sessions.md` | Session history and final gate evidence |
| `coding/README.md` | Build, run, API, and production-hardening instructions |
| `coding/backend/pom.xml` | Parent build, Java 21, coverage/PIT gates |
| `coding/backend/common/` | Problem Details, CORS, security/JWT/internal-key helpers |
| `coding/backend/*-service/` | Four deployable services |
| `coding/frontend/src/auth/refresh.ts` | Web Locks + BroadcastChannel/localStorage refresh coordination |
| `coding/frontend/src/pages/TimelinePage.tsx` | Timeline history, draft/publish, conflict recovery |
| `coding/frontend/src/pages/PageFlows.test.tsx` | Cross-page composition flows |
| `coding/frontend/vite.config.ts` | Vitest/MSW setup and per-directory coverage gates |
| `coding/deploy/docker-compose.yml` | Health-gated full-stack deployment |
| `coding/deploy/smoke.sh` | End-to-end smoke flow |
| `coding/scripts/db-check.sh` | Cross-database isolation checks |

---

## 7. Resuming later — recommended checklist

1. Read this file, then `docs/coder-sessions.md` verification evidence.
2. Run `git status` and confirm all implementation files are still present.
3. Rebuild/verify backend and frontend gates if any code changed.
4. Start Compose with `.env.example` copied to `.env`; wait for all six containers to be healthy.
5. Run `coding/deploy/smoke.sh` and `coding/scripts/db-check.sh`.
6. Tear the stack down again if the machine is being shut down.

**No required implementation work remains.** Optional follow-ups only:

- Harden Compose values for production (`POSTGRES_PASSWORD`, `BOOTSTRAP_ADMIN_PASSWORD`,
  `INTERNAL_KEY_*`, `AUTH_COOKIE_SECURE=true`, `OPENAPI_ENABLED=false`).
- Add optional Playwright end-to-end tests or Kubernetes manifests; neither is a release gate.
