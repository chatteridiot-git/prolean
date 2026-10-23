# ProLean — Timeline Gantt Technical Specification

| Field | Value |
|---|---|
| Document ID | `TS-TIMELINE-GANTT-001` |
| Version | 1.0 |
| Date | 23 October 2026 |
| Status | Implemented and verified |
| Audience | Frontend engineers, QA, reviewers |
| Related design | `docs/gantt-chart-design.md` (approved design + implementation revisions) |
| Scope area | `coding/frontend` (React/TypeScript) |

---

## 1. Overview

### 1.1 Purpose

This specification defines the technical implementation of the **Timeline Gantt**: a read-only,
version-level visualisation of a project's timeline versions rendered on the Timeline page.

### 1.2 In scope

- Rendering up to 10 timeline versions as calendar-day bars.
- Version prioritisation, ordering, and deduplication.
- Accessible, keyboard-navigable, colour-semantics-with-text chart.
- Tablets/laptop layouts with internal chart scrolling.
- Automated tests covering logic, rendering, accessibility, and page integration.

### 1.3 Out of scope

- Task-level Gantt charts, dependencies, baselines, resource levelling.
- Editing, dragging, resizing, or creating versions from the chart.
- Any backend or database change.
- Charts on other pages (only the Timeline page consumes this component).

### 1.4 Key technical constraints

1. **No new backend endpoints** — the chart consumes the existing paginated versions API.
2. **No new charting dependency** — plain React DOM + CSS.
3. **No recomputation of progress values** — `percentPlan` / `percentActual` are displayed as received.
4. **No colour-only semantics** — every lifecycle and operational status is also text.
5. **Status vocabulary single-sourced** — labels/colours live in `src/lib/status.ts`.

---

## 2. System context

```
┌──────────────────────────────┐        ┌─────────────────────────────┐
│ TimelinePage (React)         │        │ timeline-service (Spring)   │
│  ├─ useTimeline()  ──────────┼───────▶│ GET  /api/v1/projects/      │
│  ├─ useTimelineVersions(p)   │        │      {projectId}/timeline   │
│  └─ useTimelineVersions(0)   │        │ GET  /api/v1/projects/      │
│       └─▶ TimelineGantt      │        │      {projectId}/timeline/  │
│            (components/)     │        │      versions?page&size&sort│
└──────────────────────────────┘        └─────────────────────────────┘
```

The feature is frontend-only. It reuses the existing React Query hooks and cache keys from
`src/features/timeline/api.ts`.

---

## 3. Data contracts

### 3.1 Source endpoint (existing, unchanged)

```
GET /api/v1/projects/{projectId}/timeline/versions
    ?page=0
    &size=50
    &sort=versionNumber,desc
```

Response: `PageResponse<TimelineVersion>`.

### 3.2 `TimelineVersion` (existing type, `src/types/api.ts`)

Fields consumed by the chart:

| Field | Type | Use |
|---|---|---|
| `id` | `string` | Row key, dedupe identity, current-version match |
| `versionNumber` | `number` | Priority tie-breaking, display order, labels |
| `lifecycleStatus` | `DRAFT \| PUBLISHED \| SUPERSEDED \| CANCELLED` | Bar style, status tag |
| `operationalStatus` | `OPEN \| IN_PROGRESS \| CLOSED` | Status tag, accessible text |
| `startDate`, `endDate` | `string` (`YYYY-MM-DD`) | Bar geometry |
| `durationWorkdays` | `number` | Displayed value only (not geometry) |
| `percentPlan`, `percentActual` | `number` | Displayed verbatim |

### 3.3 `TimelineAggregate` (existing)

The page passes `aggregate.current?.id` as `currentVersionId` so the chart can emphasise the
actual current published version even if lifecycle labels ever differ from aggregate state.

---

## 4. Component design

### 4.1 File map

| Path | Role |
|---|---|
| `src/components/TimelineGantt.tsx` | Presentational chart component |
| `src/components/TimelineGantt.test.tsx` | Component test suite (8 tests) |
| `src/lib/status.ts` | Shared status label/colour vocabulary |
| `src/components/StatusTag.tsx` | Status chip, consumes `lib/status` |
| `src/pages/TimelinePage.tsx` | Hosts the chart card + dedicated query |
| `src/styles.css` | Gantt layout, bar, focus, and scroll styles |

**Placement rationale:** the coverage gate includes `src/components/**/*.tsx` (90% thresholds) but
only `src/features/**/*.ts` (TSX excluded). Placing the component in `components/` keeps it inside
a measured gate; this exercises the fallback recorded in the approved design (§3.1).

### 4.2 Public interface

```ts
export type TimelineGanttProps = {
  versions: TimelineVersion[];      // dedicated page-0 slice (≤ 50 API rows)
  currentVersionId?: string;        // aggregate.current?.id
};
```

The component performs **no fetching or mutation**; it is a pure function of props.

### 4.3 Internal model

```ts
type TimelineGanttRow = {
  id: string;
  versionNumber: number;
  lifecycleStatus: LifecycleStatus;
  operationalStatus: OperationalStatus;
  startDate: string;
  endDate: string;
  durationWorkdays: number;
  percentPlan: number;     // passthrough, never recomputed
  percentActual: number;   // passthrough, never recomputed
  isCurrent: boolean;
};
```

### 4.4 DOM structure

```
div[role=group][aria-label="Timeline version gantt"][tabindex=0]
└─ div.timeline-gantt-grid            (min-width: 720px, internal scroll parent)
   └─ div.timeline-gantt-row × N      (grid: 240px label | 1fr track)
      ├─ div.timeline-gantt-label     (sticky left, white background)
      │  ├─ Tag v{n} + StatusTag(lifecycle) + StatusTag(operational)
      │  ├─ "{start} – {end} · {durationWorkdays} workdays"
      │  └─ "Plan {x} · Actual {y}"
      └─ div.timeline-gantt-track
         └─ div.timeline-gantt-bar-{lifecycle}[.timeline-gantt-bar-current]
            [role=img][aria-label="…"][tabindex=-1][style: left%, width%]
            └─ span (bar caption "v{n}")
```

### 4.5 React implementation notes

- `useRef` + `useState` are declared before the empty-state early return (Rules of Hooks).
- Bar `ref` callbacks return explicit React 19 cleanup (`barRefs.current[i] = null`).
- Rows are keyed by version `id`.

---

## 5. Algorithms

### 5.1 Version selection (priority + cap)

Input: raw `versions` array (API order is `versionNumber,desc`; the implementation does not rely on it).

```
MAX_VERSIONS = 10

1. Deduplicate by `id` (first occurrence wins) → pool
2. Sort pool by versionNumber DESC (defensive; "latest" = highest versionNumber)
3. picks = []
   takeFirst(currentVersionId ? id === currentVersionId
                             : lifecycleStatus === PUBLISHED)
   takeFirst(lifecycleStatus === DRAFT)
   takeFirst(lifecycleStatus === SUPERSEDED)
   takeFirst(lifecycleStatus === CANCELLED)
4. For version of pool:
     if picks.length >= 10 → stop
     if version already in picks → skip
     append version
5. Sort picks by versionNumber ASC (display order)
6. Map to TimelineGanttRow
```

Properties:
- Exactly one current published version is prioritised (fallback to first `PUBLISHED` when the id
  is unavailable).
- At most one draft version participates in priority selection.
- Cancelled versions are always retained within the cap even though their bars are muted.
- Output is deterministic and independent of API ordering.

### 5.2 Bar geometry (calendar days, inclusive)

Let `startMs(row)` and `endMs(row)` be `dayjs(date).valueOf()` (local midnight).

```
DAY_MS      = 86_400_000
domainStart = min(startMs)
domainEnd   = max(endMs)
totalDays   = round((domainEnd − domainStart) / DAY_MS) + 1     // inclusive
leftDays(i) = round((startMs(i) − domainStart) / DAY_MS)
spanDays(i) = dayjs(endDate).diff(dayjs(startDate), 'day') + 1  // inclusive
left%(i)    = leftDays(i) / totalDays × 100
width%(i)   = spanDays(i) / totalDays × 100
```

Worked examples:

| Scenario | Domain | left | width |
|---|---|---|---|
| 02–06 Nov 2026 (5 days) | 02 Nov–06 Nov (5 days) | 0% | 100% |
| 02–06 Nov + 01–05 Dec 2026 (29-day offset, 34-day domain) | 02 Nov–05 Dec | 85.294% | 14.706% |
| Single day 02 Nov (start = end) | 1 day | 0% | 100% |

Rules:
- `durationWorkdays` never drives width; the timeline contract is calendar-day ranges.
- Bars are clipped by the track (`overflow: hidden`) and have CSS `min-width: 24px`, which keeps
  very short spans visible and readable.
- Progress percentages are **not** used for width and are **not** clamped/recomputed.

### 5.3 Keyboard focus model

Roving focus with a single tab stop:

| Key | Behaviour |
|---|---|
| `Tab` | Focuses the chart container (the only tab stop; bars have `tabindex=-1`) |
| `ArrowDown` | Enters the first bar when on the container; otherwise moves to the next bar, clamped at the last |
| `ArrowUp` | No-op on the container; otherwise moves to the previous bar, clamped at the first |
| `Home` | Jumps to the first bar |
| `End` | Jumps to the last bar |
| Any other key | Not handled; event bubbles (does not block page shortcuts) |

State: `activeIndex` is `-1` while the container itself holds focus. Focus changes are applied
synchronously via `barRefs.current[target]?.focus()`.

### 5.4 Accessible name template

```
v{versionNumber}, {LifecycleLabel}, {OperationalLabel},
{DD MMM YYYY} – {DD MMM YYYY}, {durationWorkdays} workdays,
plan {percentPlan.toFixed(2)}%, actual {percentActual.toFixed(2)}%
```

Example:

```
v1, Published, Open, 02 Nov 2026 – 06 Nov 2026, 5 workdays, plan 20.00%, actual 10.00%
```

No native `title` tooltip is used. Dates and percentages come from the shared helpers
(`formatDate`, `formatPercent`), so the chart matches the rest of the application.

---

## 6. Visual specification

### 6.1 Layout metrics

| Element | Value |
|---|---|
| Row | `display: grid; grid-template-columns: 240px 1fr; gap: 12px; padding: 8px 0` |
| Label column | `position: sticky; left: 0; background: #fff; z-index: 1` |
| Track | `height: 28px; background: rgba(15,118,110,.07); border-radius: 6px` |
| Bar | `top: 4px; bottom: 4px; border-radius: 4px; padding: 0 6px; min-width: 24px` |
| Grid minimum | `min-width: 720px` inside `overflow-x: auto` container |
| Divider | 1px `rgba(16,42,42,.08)` between rows |

### 6.2 Palette

| Lifecycle | Bar fill | Text/semantics |
|---|---|---|
| `PUBLISHED` | `#237804` | `StatusTag` green + "Published" |
| `DRAFT` | `#873800` | `StatusTag` orange + "Draft" |
| `SUPERSEDED` | `#595959` | `StatusTag` grey + "Superseded" |
| `CANCELLED` | `#a8071a` | `StatusTag` red + "Cancelled" |

- Current version: additional ring `box-shadow: 0 0 0 2px var(--prolean-ink)`.
- Bar caption: 12px / 600, white; clipped for narrow bars.
- Operational status: text chip only (blue/gold/green) — never a bar colour.
- All fills were selected to meet WCAG AA contrast with white captions.

### 6.3 Responsive behaviour

- Laptop (≥1200px): chart fills the card width; no overflow.
- Tablet (768–1199px): the chart scrolls **internally** (grid min-width 720px); labels stay
  sticky. No page-level horizontal scroll is introduced.
- Breakpoints follow the application's existing `1024px` / `640px` blocks in `styles.css`.

### 6.4 Focus styling

- Container: `outline: 2px solid var(--prolean-teal); outline-offset: 2px`.
- Bar: `outline: 2px solid var(--prolean-ink); outline-offset: 1px`.

---

## 7. State, data flow, and page integration

### 7.1 Timeline page wiring

```tsx
const versionsQuery = useTimelineVersions(project.id, historyPage);       // table
const ganttVersionsQuery = useTimelineVersions(project.id, 0);           // chart

<Card title="Timeline gantt" extra={<Typography.Text type="secondary">Up to 10 versions</Typography.Text>}>
  {ganttVersionsQuery.error ? (
    <Alert type="error" showIcon message={problemMessage(ganttVersionsQuery.error)} />
  ) : ganttVersionsQuery.isLoading ? (
    <Skeleton active title={false} paragraph={{ rows: 3 }} />
  ) : (
    <TimelineGantt versions={ganttVersionsQuery.data?.content ?? []} currentVersionId={current?.id} />
  )}
</Card>
```

### 7.2 Query semantics

- The chart always requests **page 0**, independent of the history table's `historyPage` state.
- Query key: `['projects', projectId, 'timeline', 'versions', 0]` (existing
  `queryKeys.timeline.versions`); React Query deduplicates the request while the table is also on
  page 0.
- Timeline mutations invalidate the `['projects', projectId, 'timeline']` prefix, so the chart
  refreshes after create/edit/publish/cancel/revision without new cache logic.
- No polling, no retries beyond the app defaults.

### 7.3 Render states

| State | Rendering |
|---|---|
| Initial load | antd `Skeleton` (3 rows) |
| Query error | `Alert` with `problemMessage(error)` |
| Success, 0 versions | Empty message "No versions yet." |
| Success, 1–10 versions | Bars for all versions |
| Success, >10 versions | 10 priority rows; header note "Up to 10 versions" |

---

## 8. Error handling and edge cases

| Case | Behaviour |
|---|---|
| Duplicate ids in payload | Deduplicated (first occurrence wins) |
| `currentVersionId` not in slice | Priority falls through to remaining versions; no crash |
| Lifecycle status without current id | First `PUBLISHED` in the sorted pool acts as current |
| `startDate === endDate` | 1-day bar; CSS min-width keeps it visible |
| Very short bar | Caption clipped; accessible name remains complete |
| >10 versions | Deterministic top-10 selection; history table remains the full record |
| Empty payload | Explicit empty state, no focusable chart |
| API/network error | Error alert; page-level timeline flows unaffected |
| Invalid date ranges | Backend DTO validation guarantees validity; frontend does not mutate data (documented limitation) |

---

## 9. Testing specification

### 9.1 Automated coverage

| Suite | Location | Cases |
|---|---|---|
| Component | `src/components/TimelineGantt.test.tsx` | 8 |
| Shared components | `src/components/components.test.tsx` | Status family rendering + unknown fallback |
| Page flow | `src/pages/PageFlows.test.tsx` | Gantt renders, page-0 query pinned, history + publish conflict intact |

Component test matrix:

| # | Test | Contract |
|---|---|---|
| 1 | unique versions, ascending order | dedupe, ordering, percent passthrough |
| 2 | accessible bar name | exact aria-label, no `title` |
| 3 | current/draft/superseded/cancelled priority | selection + classes |
| 4 | 12 versions | cap at 10, highest version priority |
| 5 | calendar-day geometry | inclusive 34-day domain, 29/34 offset |
| 6 | single-day version | 1-day span rendered at 100% |
| 7 | empty payload | empty state, no tab stop, key events no-op |
| 8 | keyboard | single tab stop, arrows/Home/End, clamping, unhandled keys |

### 9.2 Commands

```bash
cd coding/frontend
npm run test            # vitest run
npm run test:coverage   # vitest run --coverage (thresholds enforced)
npm run lint            # eslint
npm run build           # tsc --noEmit && vite build
```

### 9.3 Verified results (23 Oct 2026)

- Test suite: **15 files / 94 tests passed**.
- Coverage gates: all passed.
- `TimelineGantt.tsx`: 95.83% statements, 93.75% branches, 100% functions, 96.77% lines
  (gate: 90/80/90/90).
- Global: 99.41% statements, 95.33% branches, 100% functions, 99.57% lines.
- `tsc --noEmit`: clean. ESLint: 0 errors (2 pre-existing warnings in unrelated files).
- Production build: successful (`vite build`, ~1–2 min on the build host).

### 9.4 Manual acceptance checks

1. Timeline page with a published version + draft renders both bars with distinct styles.
2. Current version shows the focus ring treatment.
3. Tablet width (768px): chart scrolls internally; labels remain visible while scrolling.
4. Keyboard-only: `Tab` reaches the chart once; arrows/Home/End traverse all bars; focus visible.
5. Screen reader announces version, lifecycle, operational status, dates, workdays, plan, actual.
6. Creating/publishing/cancelling a version refreshes the chart automatically.
7. Publishing conflict modal flow still works unchanged.

---

## 10. Performance and non-functional requirements

| Aspect | Requirement / Implementation |
|---|---|
| Render size | ≤ 10 rows × (3 text lines + 1 bar) — constant work per render |
| Date math | O(N) with N ≤ 10; no layout reads, no observers |
| Network | No additional endpoint; page-0 slice shared with the table when it is on page 0 |
| Bundle | No new dependency; component + styles are a few KB |
| Security | Read-only; no new origins, storage, or user input surfaces |
| i18n/format | Dates/percentages reuse existing helpers and Jakarta-aware formatting |

---

## 11. Risks, limitations, deferred work

| Item | Status |
|---|---|
| Responsive assertions are CSS-contract + manual checks (jsdom cannot compute layout) | Accepted, documented in design §7.3 |
| Roving focus index is not reset if the focused row disappears after a refetch | Deferred (clamped, no leak) |
| Date arithmetic uses local midnight rather than Jakarta-zone helpers | Deferred (day-level rounding makes it safe) |
| "1 workdays" grammar in the accessible name | Deferred (cosmetic) |
| Invalid date ranges / non-parseable dates | Backend-guaranteed; no frontend guard (documented) |
| Chart limited to top 10 versions by design | Intentional; full history remains in the table |

---

## 12. Acceptance criteria traceability

| Design criterion (§6) | Implementation | Verified by |
|---|---|---|
| No new backend endpoint / no charting library | Existing hooks only; DOM + CSS | Code review, no dependency change |
| Read-only, versions only | No mutations, no task data | Interface, tests |
| Status vocabulary, no colour-only | `lib/status` + `StatusTag` text in every row | Tests 1–3, review |
| Keyboard + screen-reader accessible | Group + `role=img` bars, roving focus | Tests 2, 7, 8 |
| No page-level horizontal scroll on tablet | Internal scroll region, 720px grid | CSS contract + manual check |
| Percent values not recomputed | Verbatim passthrough | Test 1, code review |
| Tablet + laptop coverage | CSS breakpoints + manual checks | Design §7.3 (documented limitation) |

---

## 13. Change log

| Version | Date | Change |
|---|---|---|
| 1.0 | 23 Oct 2026 | Initial technical specification for the implemented Timeline Gantt (post-review state). |
