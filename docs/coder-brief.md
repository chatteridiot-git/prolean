# Coder Handoff — ProLean (approved v1.1)

Read, in this order, before writing code:

1. `docs/design.md` — approved technical design (text extraction of the signed-off DOCX v1.1).
2. `docs/implementation-plan.md` — milestones, repository layout, definition of done.
3. `docs/environment-check.md` — environment status from `@devops`. **Coding starts only after an APPROVAL status there.**
4. `docs/design-re-review-2026-10-20.md` — the final review fixes that must not regress.

The DOCX/PDF are authoritative if `design.md` is ever ambiguous.

## Approved decisions that override the generic agent brief

- **No API gateway is implemented or deployed in v1.** The design compares Kong, APISIX, Traefik, and Spring Cloud Gateway only. The frontend calls the four services directly through Nginx/CORS.
- **Docker Compose is the deployment target.** Kubernetes manifests are optional and are not a release gate. Do not block completion on K8s.
- **Exactly four deployable Spring Boot services.** A shared non-deployable library is allowed only for cross-cutting helpers (Problem Details, JWKS verification, internal-key client); it must not contain service business rules.
- **Credentials live only in auth-service/auth_db.** user-service never accepts or stores a password. Do not add `password_hash` to `app_user`.
- **H2 is forbidden for repository/integration tests.** Use real PostgreSQL through Testcontainers. If Docker is unavailable, use the real local PostgreSQL fallback documented in the environment check, not H2.
- **Task fields:** start/end date, generated inclusive calendar-day duration, PIC (active project member; store user ID + name snapshot), status, % complete.
- **Timeline:** one identity per project, at most one DRAFT and one PUBLISHED version, mandatory revision drafts, publish carries forward current operational status/actual % and snapshots the outgoing version, published versions are never deleted. Cancelling the only draft of a never-published timeline deletes the identity in the same transaction.
- **No zero-division:** zero working days on read → `durationWorkdays=0`, `percentPlan=0.00`, `planWarning=NO_WORKING_DAYS`; create/edit rejects 400 `NO_WORKING_DAYS`; publish is allowed.
- **Timeline dates** must lie inside the holiday coverage horizon (2025-01-01..2027-12-31), otherwise 409 `HOLIDAY_COVERAGE_REQUIRED`.
- **Publish requires `expectedCurrentUpdatedAt` whenever a current version exists**, returns 409 `CURRENT_VERSION_CHANGED` on missing/null/mismatch, and TL10/TL11 status/progress updates take the same timeline row lock as publish.
- **Idempotency:** client `requestId` on project/task/timeline/revision/publish; replay returns the original resource with 200 and `Idempotent-Replay: true`; publish replay checks `publish_request_id` before draft/timestamp checks.
- **Auth:** RS256 access JWT (15 min, `kid`), JWKS endpoint, rotating refresh cookie with `family_id` and reuse detection, 5-failure/15-minute lockout, 423 `AUTH_ACCOUNT_LOCKED`, token revocation on password set/change, role change, and deactivation.
- **Problem Details** RFC 7807 and page envelope `content/page/size/totalElements/totalPages/first/last` everywhere.
- **Coverage:** 100% line on backend core (domain/service/calculator/validator/mapper), branch gate on calculators/state machines, PIT mutation gate, per-directory frontend thresholds; documented exclusions only.

## Suggested build order

1. **M1 foundation:** parent POM (Java 21, Spring Boot 3.5.x), four modules, `common` (optional), Flyway baselines, PostgreSQL Compose service with four databases/roles, Problem Details, page envelope, request-id filter, internal-key authentication, JWKS verification, health groups, Micrometer/OTel.
2. **M2 auth-service:** `auth_credential`, `refresh_token`, login/refresh/logout/me, JWKS, lockout, password endpoints A5/A6, bootstrap admin, revoke-tokens I5; tests.
3. **M2 user-service:** users/roles/projects/members, last-admin advisory lock, self-deactivation guard, internal I1/I3/I4, revoke calls to auth-service; tests.
4. **M3 task-service:** CRUD, PIC checks, status rules, idempotency, filters, access re-check; tests.
5. **M3 timeline-service:** versions/drafts/publish/cancel/revisions, status/progress, holidays CRUD+seed, calculators, event ledger, snapshots, horizon, conflicts; tests.
6. **M4 frontend:** auth provider + cross-tab refresh, all screens, conflict/reapply, zero-working warning, responsive layout; Vitest/RTL/MSW.
7. **M5 packaging:** Dockerfiles, Compose, Nginx, init grants, smoke script.
8. **M6 hardening:** `mvn verify`, JaCoCo/PIT, frontend coverage/build, Compose smoke, then hand to `@code-reviewer`.

## Non-negotiable completion evidence

- `mvn -B clean verify` passes in every backend module (or the parent aggregator), including the coverage/mutation gates.
- `npm run lint`, `npm run test:coverage`, `npm run build` pass in `coding/frontend`.
- `docker compose up -d --build` starts PostgreSQL + four services + frontend; readiness probes pass; Flyway seeds apply.
- A smoke script proves login → project access → task create/update → timeline create/publish/revise → idempotent replay.
- No placeholders, no TODO-only methods, no skipped tests, no H2 repository tests.
- If a design rule cannot be implemented as written, stop and report the conflict to the orchestrator; do not silently change behavior.
