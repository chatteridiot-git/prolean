# ProLean Technical Design Review

- **Document:** `docs/ProLean_Technical_Design_Document.docx`
- **Reviewed:** 2026-10-19
- **Reviewer:** `design-reviewer` subagent
- **Verdict:** Changes required before approval

---

## Overall assessment

This is a well-structured and unusually specific design for a four-service system: service ownership, API contracts, timeline lifecycle, calculation rules, and deployment are mostly explicit and internally coherent. It is not ready for approval as written, however: two timeline rules can lose or permanently block business data, and the authentication design has critical trust and credential-handling weaknesses. Most remaining findings are concurrency, auditability, and operational-hardening gaps that can be fixed without changing the four-service boundaries.

## Critical

**C1 — Cancelling the initial draft leaves the timeline permanently unusable.** *Sections 6.4.1, 8.5.7, 8.5.8, 8.5.10; Section 8.5.13 error table.*
Section 8.5.8 allows the active draft to be cancelled, including version 1 before anything is published. Afterwards, creating a timeline returns `409 TIMELINE_EXISTS`, creating a revision requires a current `PUBLISHED` version and returns `409 NO_CURRENT_VERSION`, and the cancelled draft cannot be edited or published. The project is left with a timeline identity, no current version, and no supported recovery path.
**Impact:** One valid user action permanently prevents the project from having a timeline.
**Recommendation:** Allow a new initial draft when the timeline has no `PUBLISHED` version and no active `DRAFT`; alternatively, forbid cancelling the last unpublished draft or make cancellation remove the timeline identity. Add a regression test for “cancel initial draft, then recover.”

**C2 — Publishing a revision can silently revert current actual progress and status.** *Sections 8.5.9–8.5.10.*
A revision copies the current version’s dates, operational status, and actual completion (8.5.10). The current version may continue receiving status/progress updates while the draft exists (8.5.10). Publishing then applies the draft’s copied status and actual completion (8.5.9). If v1 is updated from 35% to 60% after the v2 draft is created, publishing v2 resets the current actual completion to 35% and may reopen a `CLOSED` timeline.
**Impact:** Silent loss of the most recent business progress data.
**Recommendation:** Version only planned fields; carry forward the current actual/status at publish, or reject publish if the current version changed since the draft was created. Add optimistic concurrency and a test covering “update current, then publish revision.”

**C3 — A single shared HS256 JWT secret lets any service forge ADMIN tokens.** *Sections 4.6.1, 13.2.*
All four services share the same `JWT_SECRET`; each can both verify and mint tokens. A compromised task-service or timeline-service—or anyone who obtains the secret from one container—can forge an ADMIN token and call user-service directly. There is no `kid` or key rotation.
**Impact:** A single service compromise becomes full administrative compromise of the platform.
**Recommendation:** Use RS256/EdDSA with auth-service holding the private key and the other services consuming a JWKS/public key, with `kid` and rotation. If HS256 must remain, isolate the signing secret to auth-service and use token introspection, which is a larger architectural change.

## High

**H1 — Plaintext passwords leave user-service; the document says they do not.** *Section 4.2 callout “Why auth-service and user-service are separate,” Section 4.5, Section 8.7.1.*
The login password is received by auth-service and forwarded to `POST /internal/auth/verify` in plaintext. Only the hash stays in user-service. The callout’s claim that “credentials never leave the user service” is therefore false, and internal calls are described as REST/JSON over the private Docker network with no TLS/mTLS.
**Impact:** Plaintext credentials are exposed to a second service’s memory and to intra-cluster traffic; any internal-key holder can invoke credential verification.
**Recommendation:** Either move `password_hash` to auth-service and keep user-service profile-only, or require mTLS for internal calls and scope `/internal/auth/verify` to auth-service. Correct the security claim and ensure the password is never logged or included in traces.

**H2 — Refresh-token rotation is a read-then-write race.** *Section 8.1.5; Section 9.5.*
The flow loads the token by hash, checks expiry/revocation, confirms the user, then revokes the old token and inserts a new one. Two concurrent refreshes can both pass the check, or one can observe the other’s revocation and trigger `AUTH_REFRESH_REUSED`, revoking every session. The frontend’s single-flight logic is per tab, so multiple tabs and network retries still hit this.
**Impact:** Either two valid refresh tokens exist, or a normal multi-tab refresh logs the user out globally.
**Recommendation:** Make rotation an atomic conditional update (`UPDATE ... WHERE token_hash = ? AND revoked_at IS NULL`), check affected rows, and treat the loser as reuse or apply a short grace window/token family. Add a concurrency test and cross-tab coordination.

**H3 — The last-active-admin guard is race-prone.** *Sections 8.2.6, 6.9.*
The service checks that at least one active ADMIN remains, but user/role changes are described only as single-database transactions; no row lock, advisory lock, or constraint is specified. Two concurrent requests can each observe two active admins and both commit, leaving zero active admins.
**Impact:** Administrative lockout; recovery depends on restarting with bootstrap variables.
**Recommendation:** Serialize the guard with `SELECT ... FOR UPDATE` on active admin rows or a PostgreSQL advisory lock, and add a deferred constraint/trigger where possible. Test with two concurrent deactivations/role removals.

**H4 — A holiday edit can make `totalWorkingDays` zero for an existing timeline.** *Sections 6.5, 6.6, 6.7, 8.5.11.*
Zero working days is rejected only at timeline create/update. Holidays are mutable and derived values are recalculated on read. Adding a holiday that covers the last working day of an existing timeline makes `totalWorkingDays = 0`, so the plan formula divides by zero and `durationWorkdays` becomes 0, violating the documented invariant.
**Impact:** 500 errors or undefined plan percentages after a legitimate admin action.
**Recommendation:** Handle `totalWorkingDays == 0` at read time with a defined result (for example, `0.00` plus a flag, or a stable business error), and consider blocking holiday edits that would invalidate existing timelines. Add a test for this sequence.

**H5 — Holiday coverage ends in 2027, but timelines may run for 10 years.** *Sections 6.7, 8.5.7, 10.6, Appendix A.*
Timeline ranges are allowed up to 10 years, but the seeded holiday table covers only 2025–2027. A timeline created in 2026 can run to 2036; after 2027 every weekday is treated as a working day because the holiday table is empty. The document says future years are added by an admin, but nothing enforces coverage before a timeline is created.
**Impact:** Silently overstated duration and understated plan percentages for long timelines.
**Recommendation:** Restrict timeline end dates to the covered holiday horizon, require an explicit holiday-coverage confirmation for later years, or seed/derive holidays for the full allowed range. Surface the coverage limit in the UI and API validation.

**H6 — One static internal API key with no caller identity, scoping, or rotation.** *Sections 7.8, 8.7, 13.2.*
All four services share `INTERNAL_API_KEY`. Any holder can call `/internal/auth/verify` with arbitrary credentials, enumerate users/projects through the lookup endpoints, and invoke access checks. Blocking `/internal` at the edge does not prevent lateral movement inside the cluster.
**Impact:** A single compromised service or leaked environment variable grants broad internal access, including a credential-verification oracle that bypasses auth-service.
**Recommendation:** Use per-caller keys or mTLS identities, scope `/internal/auth/verify` to auth-service only, rotate keys with an overlap window, and log/alert on internal calls, and add a test that task-service cannot call `/internal/auth/verify`.

**H7 — Timeline history is not fully auditable, and historical plan percentages are mutable.** *Sections 5.2, 5.4.3, 5.6.2, 5.6.3, 8.5.11.*
Section 5.2 says all mutable tables carry `created_at`/`updated_at` and actor columns, but `timeline_version` has no `updated_at`/`updated_by` (5.6.2), `holiday` has no `updated_by` (5.6.3), and `project` has no `updated_by` (5.4.3). Status/progress changes to the current published version therefore have no actor or timestamp, and there are no `superseded_by`/`cancelled_by` columns. Separately, 8.5.11 admits historical plan % changes when holidays are corrected.
**Impact:** The “auditable, immutable version history” claim is not met; a published version’s reported plan % can change retroactively, and progress changes cannot be attributed.
**Recommendation:** Add `updated_at`/`updated_by` and lifecycle actor columns, plus a status/progress history table or event ledger. Snapshot `duration_workdays`/`percent_plan` at supersede time if historical values must be stable. Define Asia/Jakarta conversion for `superseded_at`/`cancelled_at` when deriving `planAsOfDate`, and decide whether a supplied `asOf` is echoed in the response.

**H8 — Password reset and change do not revoke existing refresh tokens.** *Sections 8.2.6, 8.2.7.*
An admin reset or self-service password change leaves all existing refresh tokens valid for up to seven days; the document tells the user to log out on other devices. A stolen refresh token therefore survives the incident response.
**Impact:** A compromised session remains usable after the password is changed, defeating account recovery.
**Recommendation:** Revoke all refresh tokens for the user on password reset and password change; the `refresh_token.user_id` index already supports this. Consider the same on role change and deactivation, and require re-login.

## Medium

**M1 — Create and publish endpoints are not idempotent.** *Section 7, Sections 8.4.6 and 8.5.9.*
No `Idempotency-Key` is defined. A retried `POST /tasks` creates a duplicate task; a retried publish returns `404 DRAFT_NOT_FOUND` after the first success; retried project/timeline creates return unique-conflict `409`s. Browser retries, proxies, and lost responses make this realistic.
**Impact:** Duplicate business records and misleading client errors.
**Recommendation:** Support an `Idempotency-Key` header with a stored request hash/response (for example, 24-hour retention), or require client-generated UUIDs with unique constraints. Document retry semantics for POST/PATCH/DELETE.

**M2 — Only publish locks the timeline row; other lifecycle mutations race.** *Sections 6.9, 8.5.8, 8.5.10.*
Revision creation computes `version_number = max + 1` without a described lock; concurrent revisions can hit the unique constraint and surface as a 500. Concurrent draft edit and publish can publish stale fields or update a row after it became `PUBLISHED`.
**Impact:** Spurious 500s and inconsistent version data.
**Recommendation:** Lock the timeline row for revision create/edit/cancel/publish, add an `@Version` or `updated_at` check on the draft, and map constraint violations to `DRAFT_EXISTS`/`INVALID_TIMELINE_STATE`.

**M3 — Access/PIC checks and task writes have a TOCTOU window.** *Sections 6.2, 8.4.7, 8.5.1.*
Task-service checks access and PIC through separate user-service calls, then writes to `task_db`. Membership, user enabled state, or project archive status can change between the check and the write; timeline writes have the same window.
**Impact:** A task can be created for a just-removed or deactivated PIC, or written into a just-archived project.
**Recommendation:** Return a membership/project version or `updatedAt` from the access endpoint and condition the write on it, or re-check immediately before commit. At minimum, document the window and add a test.

**M4 — `CLOSED` does not enforce 100% completion.** *Sections 6.3.3, 8.4.6, 8.5.12.*
Closing sets the percentage to 100, but `PATCH progress` is allowed on any task and `PUT` accepts both status and percentage. A `CLOSED` task can be set to 50% via `PATCH progress`, or updated as `CLOSED` with 50% via `PUT`; the same applies to timeline current status/progress.
**Impact:** Reports and UI show closed work below 100%, breaking the stated invariant.
**Recommendation:** Enforce `status != 'CLOSED' OR percent = 100` in the service and a DB `CHECK`; reject progress edits on `CLOSED` or auto-reopen; define whether status or percentage wins in a full `PUT`.

**M5 — Version-history and error responses break the stated API conventions.** *Sections 7.3, 7.7, 8.5.11, 8.5.12, 8.6.*
`GET /timeline/versions` returns only `content`/`totalElements`, not the standard page envelope, and returns `400` when a project exceeds 200 versions; the holiday list also returns `year`/`content` rather than the page envelope. Separately, `NO_CURRENT_VERSION` is `409` in the error catalogue and timeline error table but `404` in 8.5.12.
**Impact:** Generic pagination clients and error handlers cannot be reused consistently; a server-side data-volume condition is reported as a client error.
**Recommendation:** Paginate versions and holidays with the standard envelope, or document them explicitly as non-paginated collections. If a cap is retained, return a successful truncated response with a flag (or a `422`) and provide a way to retrieve older versions. Standardize `NO_CURRENT_VERSION` on one status code.

**M6 — Working-day calculation performs one holiday lookup per date.** *Sections 6.5, 10.1.*
The algorithm calls `holidayRepository.existsByHolidayDate(d)` for every date in the range—up to 3,650 calls for a 10-year timeline. Section 10.1 claims the holiday set is loaded once per request and the calculation completes in ≤50 ms.
**Impact:** Database round-trip explosion on timeline reads and likely missed p95 targets.
**Recommendation:** Load holidays for `[startDate, endDate]` in one query into a `Set<LocalDate>`, then perform a single date pass. Add a 10-year benchmark test.

**M7 — Observability is below what a synchronous four-service chain needs.** *Sections 10.4, 12.5.*
Micrometer metrics are optional, there is no distributed tracing, and `/actuator/health` combines liveness/readiness with database connectivity. Only request-ID log correlation is mandatory.
**Impact:** Slow or failing internal calls are difficult to attribute; a database outage can cause unnecessary container restarts.
**Recommendation:** Make HTTP server/client metrics and dependency latency/error metrics mandatory, propagate OpenTelemetry `traceparent`, and separate liveness from readiness. Add alerts for internal-call error rate and latency.

**M8 — The 100% line-coverage gate is feasible only if narrowly scoped.** *Sections 11.2, 11.4, 11.5.*
Backend domain/service classes are feasible, but 100% line coverage across Ant Design components and hooks is expensive and often encourages assertion-free tests. The exclusion list is broad (DTOs, entities, mappers, `main.tsx`, types, MSW handlers), and branch coverage is not gated.
**Impact:** Schedule risk and false confidence in UI and orchestration code.
**Recommendation:** Keep 100% line coverage on domain/service/calculator layers, use per-directory thresholds for UI, explicitly exclude presentational components, gate branch coverage on calculators/state machines, and add mutation testing for the plan/duration formulas.

## Low

**L1 — Database-per-service is a convention, not an enforced boundary.** *Sections 4.7, 5.1, 13.3.*
Four databases and four roles share one PostgreSQL instance, but there is no `REVOKE CONNECT`, schema isolation, or per-service network policy. A bug or compromised service can connect to another service’s database with its credentials.
**Impact:** Accidental or malicious cross-service data access is possible despite the ownership rules.
**Recommendation:** Revoke `CONNECT` from other roles, use separate schemas or instances where feasible, apply per-service network policies, and add a test that each role cannot connect to the other databases.

## Strongest parts

- The ownership map is clear and defensible: auth tokens, user/project membership, tasks, and timeline/calendar each have one writer, with no shared tables or cross-database joins.
- The timeline state machine is well specified, with partial unique indexes for one draft/one published version and a row-locked publish transaction.
- The plan-completion rules are unusually precise: inclusive working days, holiday handling, half-up rounding, capping, and worked examples.
- API conventions are consistent in most places: Problem Details with stable codes, request-ID propagation, allow-listed sorting, and a common page envelope.
- The testing strategy is grounded in the real contract: Testcontainers for partial indexes/generated columns and WireMock for internal seams.
- Deployment and operations are documented concretely: Flyway per service, health checks, secrets management, CORS, backups, and a gateway-free v1 topology.
