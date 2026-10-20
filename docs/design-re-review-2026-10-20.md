# Design Re-Review Record — ProLean Technical Design Document v1.1-draft

- **Document reviewed:** `docs/ProLean_Technical_Design_Document.docx` (v1.1-draft, 20 October 2026)
- **Review date:** 20 October 2026
- **Reviewer:** `design-reviewer` subagent (independent re-review)
- **Original review:** `docs/design-review-2026-10-19.md`

## Verdict

**Ready for approval after the targeted fixes below.** All three Critical and all eight High findings from the original review are fully resolved. The re-review found one new Medium concurrency ambiguity and five Low/documentation clarifications; all were applied before the final rebuild.

## Original findings — resolution

| Finding | Status | Evidence |
|---|---|---|
| C1 — cancel initial draft leaves unusable timeline | Resolved | §6.4.1, §6.4.2 step 7, §8.5.8, §5.9, §11.3; identity deleted in the same transaction only when nothing was ever published. |
| C2 — publish reverts actual/status | Resolved | §6.4.1, §6.4.2 step 6, §8.5.9, §8.5.10, section 1.3, D11, Appendix B.3; drafts hold planned dates/notes only, publish carries forward current operational values and snapshots the outgoing version. |
| C3 — shared HS256 secret | Resolved | RS256/JWKS throughout §4.5, §4.6.1, §8.1, §13.2, D4, glossary; no HS256/JWT_SECRET text remains. |
| H1 — plaintext password crossing services | Resolved | Credentials and hashes only in auth-service (`auth_db.auth_credential`); user-service is profile-only; legacy `/internal/auth/verify` removed; A5/A6 password endpoints documented in §8.1.9. |
| H2 — refresh rotation race | Resolved | Atomic conditional revocation, token families, reuse detection, cross-tab Web Locks/BroadcastChannel lock (§4.6.1, §5.3.2, §8.1.5, §9.5). |
| H3 — last-admin race | Resolved | `pg_advisory_xact_lock` guard (§4.6.3, §6.9, §8.2.6) with concurrency tests. |
| H4 — holidays reduce timeline to zero working days | Resolved | `durationWorkdays=0`, `percentPlan=0.00`, `planWarning=NO_WORKING_DAYS`; create/edit rejects 400 `NO_WORKING_DAYS`; publish allowed; Appendix B.5. |
| H5 — holiday horizon vs long timelines | Resolved | Coverage horizon derived from holiday table (2025-01-01..2027-12-31); writes outside return 409 `HOLIDAY_COVERAGE_REQUIRED` (§6.5, §6.7, §8.5.7). |
| H6 — single shared internal key | Resolved | Per-caller `X-Internal-Key-Id` + `X-Internal-Api-Key`, constant-time compare, endpoint scope matrix, rotation overlap (§7.8, §8.7, §13.2). |
| H7 — auditability / mutable history | Resolved | Actor/timestamp columns, `timeline_version_event` ledger, frozen snapshot fields, paginated history (§5.6.2–§5.6.4, §6.6, §8.5.11). |
| H8 — password change/reset does not revoke sessions | Resolved | A5/A6, role change, and deactivation revoke all refresh families; auth-service re-checks enabled/roles on refresh (§4.6.3, §8.1.9, §8.2.6–§8.2.7, §8.7.5). |
| M1 — non-idempotent creates | Resolved | `requestId` unique constraints for project/task/timeline/revision, `publish_request_id` for publish, replay returns 200 + `Idempotent-Replay: true` (§7.2, §8.5.9). |
| M2 — only publish locked the row | Resolved | All timeline lifecycle writes lock the timeline row; expected-timestamp conflicts return 409 `DRAFT_MODIFIED`/`CURRENT_VERSION_CHANGED` (§6.9). |
| M3 — TOCTOU on access/PIC | Resolved | Access re-check immediately before commit with rollback; residual window documented (§6.2, §6.9, §14.1) and tested with WireMock. |
| M4 — CLOSED not enforcing 100% | Resolved | Service rules, 409 `TASK_CLOSED`/`TIMELINE_CLOSED`, 400 `INVALID_COMPLETION_FOR_CLOSED`, DB CHECK constraints (§5.5, §5.7, §6.3.3, §6.4.4, §8.4.6, §8.5.12). |
| M5 — envelope/error inconsistencies | Resolved | Page envelope for history and holidays, `NO_CURRENT_VERSION` standardized to 409, paginated history (§7.3, §8.5.11, §8.5.13, §8.6.2). |
| M6 — per-date holiday lookup | Resolved | Single `findByHolidayDateBetween` query + in-memory set, single date pass (§6.5, §10.1). |
| M7 — observability | Resolved | Mandatory Micrometer server/client/dependency metrics, OpenTelemetry `traceparent`, separate liveness/readiness probes (§4.3, §10.4, §12.5). |
| M8 — coverage gate scoping | Resolved | 100% line gate on backend core, per-directory frontend thresholds, branch gates on calculators/state machines, PIT mutation gate, documented exclusions (§11.5). |
| L1 — DB-per-service unenforced | Resolved | `REVOKE CONNECT` for other databases, per-role grants, CI cross-database denial test (§13.3, §11.2, §11.3, §11.6). |

## New findings from the re-review and dispositions

| ID | Severity | Finding | Disposition |
|---|---|---|---|
| F1 | Medium | Publish carry-forward could race with TL10/TL11 status/progress updates because only the parent timeline row was locked. | **Fixed.** §6.9 and §8.5.9 now include TL10/TL11 in the timeline-row lock set and require `expectedCurrentUpdatedAt` whenever a current version exists (409 `CURRENT_VERSION_CHANGED` on missing/null/mismatch). §9.6 requires the SPA to send both expected timestamps. Concurrency test added to §11.2/§11.3. |
| F2 | Low/Medium | Draft persistence of `operational_status`/`percent_actual` was ambiguous (NOT NULL columns, CHECK constraint, example showed IN_PROGRESS/35 on a DRAFT). | **Fixed.** §5.6.2, §5.7, §6.4.1, §8.5.5, and §8.5.10 now state DRAFT/CANCELLED rows store OPEN/0; draft responses report those defaults; PUBLISHED rows carry operational values. Example corrected. |
| F3 | Low | JWKS retirement configuration was under-specified. | **Fixed.** §4.6.1 and §13.2 add `JWT_RETIRED_PUBLIC_KEYS`; rotation is documented as a manual, reviewed operation in release 1. |
| F4 | Low | User/member/holiday creates are not idempotent. | **Documented.** §7.2 states these creates have no `requestId` in release 1 and a retry returns the normal duplicate 409; callers should re-read. |
| F5 | Low | Publish replay check order was not stated. | **Fixed.** §8.5.9 states `publish_request_id` is checked before draft-exists and timestamp checks. |
| F6 | Low | Documentation nits: unused `must_change_password`; holiday example date already seeded; missing 17 March row in the worked example; TL2 `asOf` behavior unstated. | **Fixed.** Column removed, example changed to 2026-12-28, 17 March row added, TL2 `asOf` behavior documented in §8.5.3. |

## Final verification

- DOCX rebuilt with 19 diagrams and 85 tables; TOC updated by Word COM; 93 pages; PDF exported.
- DOCX XML sweep: 0 occurrences of `HS256`, `JWT_SECRET`, `INTERNAL_API_KEY`, `/internal/auth/verify`, `must_change_password`, `shared internal key`.
- New content confirmed present in the DOCX: mandatory `expectedCurrentUpdatedAt`, DRAFT/CANCELLED OPEN/0 semantics, `JWT_RETIRED_PUBLIC_KEYS`, corrected holiday example, 17 March worked-example row, TL10/TL11 lock scope.
- Diagram QA: architecture, auth/user/task/timeline ERDs, and both state diagrams re-rendered with readable aspect ratios; no overlapping labels.

**Conclusion:** The revised design is internally consistent and ready for user approval. Implementation must not start until the approval checklist in Section 15 is signed off.
