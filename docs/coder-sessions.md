# Coder Session Plan

Each session is scoped to keep the coder within its step budget and to produce a reviewable increment. A session is complete only when its listed tests/build commands pass.

## Session 1 — M1 workspace foundation (complete)

Scope: parent POM, four modules, shared cross-cutting code, Flyway skeletons, Compose/PostgreSQL init, README, build verification.

Exit evidence: `mvn -B -DskipTests package` succeeds; service modules compile and have the required actuator endpoints/config; Compose file and DB init script exist.

## Session 2 — user-service core

Scope:
- Flyway schema for `app_user`, roles, projects, project members, audit columns, constraints.
- User CRUD/deactivate, role assignment, self-deactivation guard, last-active-ADMIN advisory lock.
- Project CRUD/archive, membership add/remove, archived-project read/write rules.
- Internal endpoints I1/I3/I4 with per-caller internal keys.
- Calls to auth-service I5 on role change and deactivation (WireMock tests).
- Unit, MockMvc, repository/Testcontainers, and concurrency tests.

Exit evidence: user-service `mvn verify` passes; endpoint table from design §8.2/§8.3 implemented; cross-DB access is impossible with its role.

## Session 3 — auth-service

Scope:
- Flyway schema for `auth_credential`, `refresh_token`, key material/metadata.
- RS256 signing keys, JWKS endpoint, access-token claims.
- Login, refresh rotation with token families/reuse detection, logout, `/auth/me`.
- 5-failure/15-minute lockout and 423 response; password set/change A5/A6; revoke all families.
- Bootstrap admin flow (user-service I3 + credential creation), internal I5.
- Unit, MockMvc, Testcontainers, WireMock, and concurrency tests (parallel refresh).

Exit evidence: auth-service `mvn verify` passes; JWKS endpoint returns the active `kid`; replay of a consumed refresh token revokes the family.

## Session 4 — task-service

Scope:
- Flyway schema for tasks, generated inclusive calendar-day duration, PIC user ID + name snapshot.
- CRUD, status/progress rules (`CLOSED ⇒ 100`, progress edit on closed → 409), PIC active-member validation via user-service I4, archived-project writes.
- Idempotent create (`requestId`), filters/pagination, access re-check before commit.
- Unit, MockMvc, Testcontainers, and idempotency tests.

Exit evidence: task-service `mvn verify` passes; task duration and PIC rules match design §6.3/§8.4.

## Session 5 — timeline-service, part 1

Scope:
- Flyway schema: timeline identity, versions, event ledger, holidays, snapshots, partial unique indexes.
- Holiday seed 2025–2027 and Admin CRUD.
- Working-day calculator, plan-percent calculator (half-up 2 decimals), zero-working-day behavior, horizon enforcement.
- Unit tests for every calculator boundary and DB constraints.

Exit evidence: calculator test matrix and migration tests pass.

## Session 6 — timeline-service, part 2

Scope:
- Create/edit/cancel draft, publish with row lock + `expectedCurrentUpdatedAt` + carry-forward + snapshots + event, revisions, status/progress (TL10/TL11 lock), history pagination, idempotent request/publish replay.
- Cancelling the only draft of a never-published timeline deletes the identity; otherwise only the draft is cancelled.
- Integration/concurrency tests (publish vs progress, draft conflicts, replay order).

Exit evidence: timeline-service `mvn verify` passes; design §6.4–§6.7 and §8.5–§8.6 behaviors covered.

## Session 7 — frontend

Scope:
- Vite/React/TypeScript/Ant Design app; auth provider with in-memory access token and cross-tab single-flight refresh.
- Login, projects/members, tasks, timeline current/draft/history, publish/cancel/revise, users/roles, holidays admin.
- Conflict reload/reapply, zero-working warning, role-based visibility, responsive laptop/tablet layout.
- Vitest/RTL/MSW tests and per-directory coverage gates.

Exit evidence: lint, coverage, and production build pass; no secret/token persisted in localStorage.

## Session 8 — integration and release hardening

Scope:
- Dockerfiles, Compose frontend/Nginx, health checks, full stack startup.
- Smoke script: login → project access → task → timeline create/publish/revise → idempotent replay.
- Full backend/frontend gates, JaCoCo/PIT, OpenAPI parity, dependency/vulnerability checks.
- Fix all `@code-reviewer` findings and re-run evidence.

Exit evidence: `docker compose up -d --build` reaches healthy state; smoke script passes; all gates green; review report has no open Critical/High findings.

## Verification evidence (21 October 2026)

- Backend: `cd coding/backend && mvn -B clean verify` -> BUILD SUCCESS. 216 tests (common 34, auth 53, user 48, task 25, timeline 56), 0 failures/errors.
- Backend review regressions: disabled users are denied internal access; timeline status/progress/cancel paths re-check access before commit; holiday coverage now uses whole calendar years (2025-01-01..2027-12-31) on both the read and write paths; CORS exposes the horizon headers; page-flow, conflict, OpenAPI path, cookie-clearing, JWT-claim and cross-database tests pass.
- JaCoCo: every module reported `All coverage checks have been met` (100% lines on the gated business packages; calculator branch gate met).
- PIT: timeline `WorkingDayCalculator` -> 17 mutations, 16 killed, mutation score 94%, line-coverage gate met.
- Frontend: `npx vitest run --coverage` -> 14 files / 84 tests passed; statements 100%, branches 94.96%, functions 100%, lines 100%; per-directory thresholds met. Page flows cover archived read-only views, holiday horizon, history pagination, conflict recovery and idempotency retry.
- Frontend static checks: `npm run lint` -> 0 errors (2 fast-refresh warnings); `npm run build` -> success.
- Containers: `docker compose -f coding/deploy/docker-compose.yml up -d --build` -> postgres, auth, user, task, timeline and frontend all healthy.
- Smoke: `bash coding/deploy/smoke.sh` -> all 7 steps passed (login, project create/replay, list, task create, timeline publish/revision publish).
- Cross-database isolation: `bash coding/scripts/db-check.sh` -> all checks passed.
- OpenAPI: generated documents verified by integration tests in all four services; runtime docs remain gated by `OPENAPI_ENABLED` and are disabled in Compose.
