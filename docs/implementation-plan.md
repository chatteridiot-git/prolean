# ProLean Implementation Plan (post-approval)

**Inputs**
- Approved design: `docs/ProLean_Technical_Design_Document.docx` / `.pdf` (v1.1-draft, 20 Oct 2026)
- Markdown mirror for agents: `docs/design.md`
- Re-review record: `docs/design-re-review-2026-10-20.md`
- Environment gate: `docs/environment-check.md` (produced by `@devops`; coding starts only after APPROVAL)

**Non-negotiable scope**
- Exactly four Spring Boot services: `auth-service` (8081), `user-service` (8082), `task-service` (8083), `timeline-service` (8084).
- One PostgreSQL instance with `auth_db`, `user_db`, `task_db`, `timeline_db`; one role per service; no cross-database access.
- Credentials and password hashes exist only in `auth_db`/auth-service. user-service owns profiles, roles, projects, and membership.
- RS256 access JWT (15 min) with `kid` and JWKS; rotating refresh token in HttpOnly cookie with token families and reuse detection.
- Timeline: one identity per project, at most one DRAFT and one PUBLISHED version, mandatory revision drafts, publish carries forward current operational status/actual %, superseded versions keep snapshots and an event ledger. Cancelling the only draft of a never-published timeline deletes the identity.
- Task duration = inclusive calendar days. Timeline duration = inclusive working days excluding weekends and Indonesian holidays (2025–2027 seed). Plan % = elapsed working days × (100 / total working days), rounded half-up to 2 decimals; zero working days → 0.00 + `NO_WORKING_DAYS`.
- No API gateway deployment. Docker Compose is the deployment target. Kubernetes manifests are optional documentation, not a release gate.
- Responsive React/TypeScript/Vite/Ant Design frontend for laptop/tablet.
- Idempotent `requestId` for project/task/timeline/revision/publish; `publish_request_id` replay check first.
- RFC 7807 Problem Details and the standard page envelope.
- Coverage: 100% line on backend core (domain/service/calculator/validator/mapper), branch gate on calculators/state machines, PIT mutation gate, per-directory frontend thresholds. Tests must assert outcomes, not just execute lines.

## Repository layout

```
coding/
  backend/
    pom.xml                  # parent, Java 21, Spring Boot 3.5.x
    common/                  # optional shared library (errors, JWKS filter, internal-key client)
    auth-service/
    user-service/
    task-service/
    timeline-service/
  frontend/                  # Vite + React + TypeScript + Ant Design
  deploy/
    docker-compose.yml
    postgres/init/01-databases.sh
    nginx/                   # frontend static config
  scripts/                   # smoke tests, DB checks
  README.md
```

If a `common` module is used, it must not become a fifth deployable service and must not contain service-specific business rules. Duplicating small security/error helpers is acceptable when it keeps the four services independently buildable.

## Milestones

**M1 — Backend foundation**
- Parent POM, dependency management, four Spring Boot modules, shared conventions.
- Flyway baselines and `docker-compose.yml` PostgreSQL service with four databases/roles.
- Problem Details, pagination envelope, request-id filter, internal-key authentication scaffold, JWKS validation filter.
- Health groups: `/actuator/health/liveness`, `/actuator/health/readiness`; Micrometer + OpenTelemetry wiring.

**M2 — auth-service + user-service**
- Auth: `auth_credential`, `refresh_token`, login lockout (5 failures/15 min), RS256 signing, JWKS endpoint, refresh rotation/reuse, logout, `/auth/me`, A5 admin set/reset password, A6 self change, bootstrap admin, revoke-tokens internal endpoint.
- User: users/profiles, roles, projects, project members, last-active-ADMIN advisory lock, self-deactivation guard, archive project, internal I1/I3, access check I4, revoke-tokens calls I5, role/status changes revoke refresh families.
- Tests: unit, MockMvc, repository/Testcontainers, WireMock for cross-service, concurrency for refresh rotation and last-admin.

**M3 — task-service + timeline-service**
- Task: CRUD, PIC membership + name snapshot, calendar-day duration (generated column), status rules (`CLOSED ⇒ 100`, progress edit on closed → 409), idempotent create, filters/pagination, access re-check before commit.
- Timeline: aggregate/current/versions, create draft, edit draft, cancel (initial-draft identity deletion), publish (row lock, mandatory `expectedCurrentUpdatedAt` when current exists, carry-forward + snapshot + event), revisions, status/progress, history pagination, holidays CRUD + seed, working-day and plan calculators, horizon enforcement, zero-working-day warning, task/timeline event ledger.
- Tests: exhaustive calculator tests, state machines, repository constraints, concurrency (publish vs progress), idempotency replay, holiday horizon.

**M4 — Frontend**
- Auth provider with in-memory access token, cross-tab single-flight refresh (Web Locks + BroadcastChannel fallback), route guards.
- Screens: login, projects list/detail, project members, tasks table/form, timeline aggregate with current + draft + history, publish/cancel/revise flows, conflict reload/reapply, users/roles, holidays admin, responsive laptop/tablet.
- MSW/RTL tests for auth, refresh, conflicts, zero-working warning, role visibility; per-directory coverage thresholds.

**M5 — Packaging and deployment**
- Dockerfiles for each service and frontend; Compose with health checks and dependencies.
- PostgreSQL init/grants: `REVOKE CONNECT` on other databases, per-role ownership, cross-database denial check.
- Smoke script: login → projects → task → timeline create/publish/revise → idempotent replay.
- Optional K8s manifests only if time permits; not a release gate.

**M6 — Hardening and review**
- Run `mvn verify` per service (unit + slice + repository/integration + concurrency), JaCoCo core gate, PIT gate.
- Run frontend lint, tests with coverage, production build.
- Run Compose smoke test.
- Hand to `@code-reviewer`; fix all findings and rerun gates.

## Environment gate

`@devops` must produce `docs/environment-check.md` with **APPROVAL** before M1 coding. Required: JDK 17+, Maven, Node/npm, PostgreSQL, Docker engine + Compose. If Docker is unavailable, Testcontainers-based tests are blocked; the fallback is a real local PostgreSQL configured through environment variables, documented as an environment deviation. H2 is never acceptable.

## Definition of done

Matches design §11.8: every in-scope endpoint implemented, every business rule covered, all backend/frontend gates green, Compose starts with Flyway and seed data, smoke flow passes, no critical/high vulnerabilities, generated OpenAPI matches the approved API tables, and the design document is updated for any approved deviation.
