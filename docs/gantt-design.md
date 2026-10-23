# ProLean Gantt Chart — Technical Design (Addendum)

## Document control

| Item | Value |
|---|---|
| Feature ID | `GANTT-1` |
| Document | `docs/gantt-design.md` |
| Version | 1.0 |
| Status | **Draft for design review** |
| Date | 22 October 2026 |
| Owner | Solution Architecture |
| Baseline | `docs/design.md`, approved design v1.1, 20 October 2026 |
| Relation to baseline | Additive frontend feature. This document **does not modify** `docs/design.md` and **changes no existing contract**. |
| Implementation gate | Coding starts only after this document is signed off (Section 10), following the plan in Section 11. |

### 0.1 What "additive" means here

This feature adds one read-only frontend route and frontend-only composition code. It introduces:

- no new backend endpoint, request field, response field, database column, migration, authentication rule, CORS rule, or gateway change;
- no change to task, timeline, holiday, or project semantics;
- no change to the existing `/projects/:projectId/timeline` page;
- no new runtime dependency (Section 6).

Every value the chart displays comes from an existing response defined in design v1.1:

| Displayed value | Existing source | Design reference |
|---|---|---|
| Task title, dates, duration, PIC, status, percent complete | `GET /api/v1/projects/{projectId}/tasks` page envelope | v1.1 §8.4.5, Table 47 |
| Published baseline and draft dates, working-day duration, plan %, actual %, plan as-of date, warning | `GET /api/v1/projects/{projectId}/timeline` aggregate | v1.1 §8.5.6, Table 50 |
| Holidays (national and joint leave) | `GET /api/v1/holidays?year=...` page envelope | v1.1 §8.6.2, Table 55 |
| Holiday coverage horizon | `X-Holiday-Coverage-Start` / `X-Holiday-Coverage-End` response headers | v1.1 §6.7; implemented in `HolidayController` |
| Project period, name, status | Project detail already loaded by `ProjectLayout` | v1.1 §8.3.5; `ProjectLayout.tsx` |

The chart is a **view**. It computes only geometry and calendar shading. It never recomputes task duration, timeline working-day duration, or `percentPlan`; those are server-owned business values (v1.1 §6.5, §6.6) and are displayed as returned.

### 0.2 Approved scope assumed by this document

The user approved the following scope; this document does not re-open it:

1. Content: task bars, published timeline baseline bar, draft revision overlay, Indonesian holiday and weekend shading, today marker.
2. Interaction: **read-only v1**. View, zoom, scroll, filter, and click a bar for details. No drag, no resize, no writes from the chart.
3. Data: frontend composition only. Reuse existing endpoints. No backend change, no new API, no cross-service call.
4. Placement: new **Gantt** tab and route `/projects/:projectId/gantt`; the existing Timeline page remains intact.

---

## 1. Goals, non-goals, user stories

### 1.1 Goals

- G1: Give project members one view that answers "what work happens when, against which approved baseline, and which days are non-working".
- G2: Make the baseline/draft distinction visible without leaving the project context and without duplicating the Timeline page's edit controls.
- G3: Show Indonesian non-working days (Saturday, Sunday, national holiday, joint leave) as calendar shading so a date range can be read correctly at a glance.
- G4: Stay read-only, so the feature cannot introduce a new write path, concurrency risk, or audit gap.
- G5: Compose exclusively from existing endpoints, with loading and failure states that degrade independently.
- G6: Keep all business logic in pure, exhaustively tested frontend functions so the existing coverage gates remain meaningful.
- G7: Remain usable with hundreds of tasks and multi-year project spans.

### 1.2 Non-goals

| Non-goal | Reason |
|---|---|
| Drag, resize, inline date editing | Read-only v1 scope. All edits stay on Tasks and Timeline. |
| Task dependencies, critical path, resource levelling | Explicitly out of release-1 scope in design v1.1 §2.3. |
| Editing or publishing timelines from the chart | Timeline page remains the only lifecycle surface (v1.1 §8.5, §9.6). |
| Choosing an arbitrary historical baseline version | The chart shows the current published version and the active draft, matching the TL1 aggregate. Version history remains on the Timeline page. |
| Server-side filtering or a dedicated Gantt endpoint | No new API allowed. Filtering is client-side over the loaded set. |
| Export to PNG/PDF/Excel | Not in scope. |
| Phone-first layout | Laptop and tablet remain the supported targets (v1.1 §10.5); phone is best effort. |
| Persisting zoom/filter state in the URL or browser storage | Local component state only; keeps the route free of contract-like parameters. |
| Recomputing `percentPlan`, working-day duration, or task duration | Server-owned; duplicating the formula in the browser would create two sources of truth. |

### 1.3 User stories

| ID | Story |
|---|---|
| US-1 | As a project member, I want to see all tasks positioned on a calendar so I can judge sequencing and overlap without opening each task. |
| US-2 | As a project member, I want to see the approved published timeline as a baseline bar so I can tell whether tasks fall inside the approved period. |
| US-3 | As a project member, I want to see an active draft revision as a distinct overlay so I know a proposed change exists but is not yet active. |
| US-4 | As a project member, I want weekends and Indonesian holidays shaded so I understand why a working-day duration differs from a calendar span. |
| US-5 | As a project member, I want to switch between day, week, and month zoom and scroll horizontally so I can inspect either a single week or a whole year. |
| US-6 | As a project member on a tablet, I want the task names to stay visible while I scroll the calendar horizontally. |
| US-7 | As a keyboard or screen-reader user, I want to reach every bar, hear its dates/status/progress, and open its details without a mouse. |
| US-8 | As a project member, I want to filter by status, PIC, or title so I can focus on a subset without changing the time axis. |
| US-9 | As a project member, I want to click a bar and see exact dates, duration, PIC, status, progress, and (for the baseline) plan/actual percentages. |
| US-10 | As a project member, I want the chart to remain useful if the timeline or holiday service call fails, rather than showing a blank page. |

### 1.4 Acceptance criteria

Each criterion is testable. AC-16 is a negative criterion: the Gantt route must not issue writes.

| ID | Acceptance criterion |
|---|---|
| AC-1 | Navigating to `/projects/:projectId/gantt` renders the Gantt page inside `ProjectLayout`; a **Gantt** tab is present between **Timeline** and **Members**; `/projects/:projectId/timeline` still renders the existing Timeline page unchanged. |
| AC-2 | Every loaded task with a valid, non-inverted date range is rendered as exactly one bar (entries with invalid dates are skipped and counted, §4.3). The bar starts at the task's `startDate` cell and ends at the end of its `endDate` cell, so a one-day task is one day wide. |
| AC-3 | When the aggregate contains a current published version, a baseline row renders with the version number, lifecycle/operational status, and a bar spanning `startDate`..`endDate` inclusive. A plan marker is drawn at the end of `planAsOfDate` when that date is before `endDate`, and omitted when it is on/after `endDate`. When there is no current version, no baseline bar is drawn and an explanatory message is shown. |
| AC-4 | When the aggregate contains a draft and the Draft toggle is on (the default), a visually distinct draft overlay renders in its own row, labelled as not active, and shows no progress fill. |
| AC-5 | With holiday shading on (the default), weekend shading covers every Saturday and Sunday in the rendered axis, and holiday shading covers every holiday returned for the intersecting years, distinguishes `NATIONAL` from `JOINT_LEAVE`, and both are explained in a visible legend. |
| AC-6 | A today marker is drawn at the start of today's date in Asia/Jakarta when that date is inside the axis, and is absent when it is outside. |
| AC-7 | Zooming to day, week, or month changes the pixels-per-day, the header tick granularity, and the bar geometry deterministically; the selected zoom is exposed as the current value of the zoom control (checked/selected state, not colour alone). |
| AC-8 | The chart scrolls horizontally; the header stays visible vertically and the task label column stays visible horizontally. |
| AC-9 | Clicking a task, baseline, or draft bar opens a details drawer containing the fields listed in §2.7. Closing the drawer returns focus to the bar that opened it. |
| AC-10 | Bars are reachable by keyboard. Arrow keys move focus between bars, `Enter`/`Space` opens details, `Escape` closes details. Each bar exposes an accessible name containing kind, title/version, dates, and progress/status where applicable. |
| AC-11 | Status, PIC, and title filters hide non-matching task bars; the axis domain does not change when filters change; the visible-count text updates. |
| AC-12 | Task loading follows `page`/`size`/`sort` sequentially with `size=100`, stops at `last` or the documented cap, de-duplicates by task id, and shows a truncation notice when not all server tasks were loaded. |
| AC-13 | A timeline request failure (other than a 404 that means "no timeline") does not prevent task bars from rendering; a non-blocking warning is shown. A 404 renders the "no timeline" state, not an error. |
| AC-14 | A holiday request failure does not prevent the chart from rendering; weekend shading and task/baseline bars remain, and a warning names the years whose shading is unavailable. |
| AC-15 | Empty data shows an explanatory empty state with links to Tasks and Timeline. An archived project shows the shared archived banner, the chart still renders read-only, and no mutation control exists anywhere on the Gantt page. |
| AC-16 | Loading the Gantt route issues only `GET` requests. No `POST`, `PUT`, `PATCH`, or `DELETE` is issued by page load, zoom, scroll, filter, or details interaction. |
| AC-17 | Date-only values never shift by one day: geometry is computed from integer day ordinals derived with `Date.UTC`, and "today" comes from the existing `jakartaToday()` helper, not from `new Date()` or `new Date('YYYY-MM-DD')`. Unit tests cover month, year, leap-day, and weekend boundaries. |
| AC-18 | The frontend coverage thresholds are unchanged and still pass with the new files included: global 95/95/95/85; `src/lib` and `src/features` 100/100/100/90; `src/components` 90/90/90/80; the pre-existing 84 tests in 14 files remain green. |

---

## 2. UX specification

### 2.1 Tab placement and routing

Existing routing (verified in `src/app/App.tsx`):

```text
/projects/:projectId  -> ProjectLayout
    index             -> Navigate to tasks
    tasks             -> TasksPage
    timeline          -> TimelinePage
    members           -> MembersPage
```

The feature adds one sibling route:

```text
    gantt             -> GanttPage
```

`src/app/ProjectLayout.tsx` currently derives the active tab from `location.pathname.endsWith('/timeline')`, then `/members`, and otherwise falls back to `tasks`. It must gain a `/gantt` branch **before** the fallback; otherwise the Gantt route would show the Tasks tab as selected. The tab array becomes:

```ts
[
  { key: 'tasks', label: 'Tasks' },
  { key: 'timeline', label: 'Timeline' },
  { key: 'gantt', label: 'Gantt' },
  { key: 'members', label: 'Members' },
]
```

The existing index redirect to `tasks` is unchanged. The Timeline page and its route are untouched.

### 2.2 Page layout

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ PageHeader: "Gantt"                                                         │
│ subtitle: "Read-only schedule. Bars use inclusive calendar days."           │
│ (ProjectLayout already renders project name, status, period, archived note) │
├─────────────────────────────────────────────────────────────────────────────┤
│ Toolbar (Card):  [Day|Week|Month]  [Today]                                  │
│                  [Status ▾] [PIC ▾] [Search title] [Draft ◉] [Holidays ◉]   │
│                  "126 of 480 tasks"                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│ Notices: truncation · no timeline · timeline unavailable · holiday warning  │
├─────────────────────────────────────────────────────────────────────────────┤
│ Chart (single scroll container, max-height 560 px)                          │
│ ┌───────────────────────┬─────────────────────────────────────────────────┐ │
│ │ sticky corner         │ sticky header: month groups / day-week labels   │ │
│ ├───────────────────────┼─────────────────────────────────────────────────┤ │
│ │ Baseline · v3         │ ███████████████████████████████████  (row 0)   │ │
│ │ Draft · v4 (inactive) │ ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ (row 1)                    │ │
│ │ Task A     40%        │      ███████░░░                                  │ │
│ │ Task B    100%        │  ███████████                                    │ │
│ │ ...                   │      │ today                                  │ │
│ └───────────────────────┴─────────────────────────────────────────────────┘ │
│ Legend: task · progress · baseline · draft · weekend · national · joint · no data        │
└─────────────────────────────────────────────────────────────────────────────┘
│ Drawer (right, 480 px): details of the selected bar                         │
```

- The chart is one horizontally and vertically scrollable container. The task label column is a set of cells with `position: sticky; left: 0`; the header row uses `position: sticky; top: 0`; the top-left corner is sticky on both axes. This avoids two scroll panes that would need synchronization.
- Baseline and draft rows sit above the task rows and scroll vertically with the content; only the label cells, the header, and the corner are sticky.
- The toolbar and legend are outside the scroll container.
- The **Draft** toggle is disabled when the aggregate has no draft. The **Holidays** toggle is always available and hides only holiday bands; weekend shading remains. Neither toggle refetches data.

### 2.3 Zoom levels

| Level | Pixels per calendar day | Header primary row | Header tick row | Tick label | Typical use |
|---|---|---|---|---|---|
| Day | 32 | Month (`MMMM YYYY`) | Every day | `DD` (thinned if needed) | Inspect a sprint or a week |
| Week | 8 | Month | Every Monday | `DD MMM` | Inspect a quarter |
| Month | 3 | Year (`YYYY`) | First of each month | `MMM YYYY` | Inspect a multi-year programme |

Rules:

- `pxPerDay` is a constant per level; there is no free zoom slider in v1.
- Zoom is local page state, default `week`.
- Changing zoom keeps the **centre date** of the current viewport approximately stable: compute the day index at the viewport centre before the change and scroll to keep it at the centre afterwards. `GanttChart` owns this: a `useLayoutEffect` keyed on `zoom` records the centre index from `scrollLeft + viewportWidth / 2` with the previous `pxPerDay` and restores it after the new scale is applied. The page only supplies the new `zoom` value.
- On mount, the chart scrolls today to the centre when today is inside the axis; otherwise it scrolls to the axis start. This is a one-time effect, not a per-render scroll.
- "Today" button re-centres the today marker; it is disabled when today is outside the axis.
- Zoom controls use Ant Design `Segmented` with visible text labels, not icons alone.

### 2.4 Horizontal scroll, sticky column, and sticky header

- The scroll container has `overflow: auto`, a fixed `max-height: 560px` (laptop) / `440px` (tablet), and `overscroll-behavior: contain`.
- The content width is `dayCount * pxPerDay`; the label column is 280 px (laptop), 232 px (tablet), 168 px (below 768 px).
- Each row is a horizontal flex/grid of `label | track`; the label cell is sticky left with an opaque background and `z-index` above bars; the header cell is sticky top; the corner is sticky both and has the highest `z-index`.
- The header is rendered only for the visible horizontal window (plus one viewport of overscan), so a 10-year day zoom does not create ~3,650 header labels.
- Scroll events update `scrollLeft` through a `requestAnimationFrame`-throttled state update; only the header tick window depends on it, so bars and bands are not re-created while scrolling.
- `viewportWidth` is read from the container (`clientWidth`/`getBoundingClientRect().width`) with a 1200 px fallback when it reports 0, which keeps header windowing deterministic under jsdom; a throttled `window.resize` listener refreshes it. No `ResizeObserver` is required for v1.

### 2.5 Responsive behaviour

| Area | Laptop ≥1200 px | Tablet 768–1199 px | <768 px (best effort) |
|---|---|---|---|
| Label column | 280 px | 232 px | 168 px |
| Toolbar | One wrapping row | Two wrapping rows | Stacked controls |
| Chart height | 560 px | 440 px | 360 px |
| Row height | 30 px (day/week), 24 px (month) | Same | Same |
| Details drawer | 480 px | 100% width | 100% width |
| Legend | Inline | Wrapped | Two columns |
| Bar hit target | Height ≥24 px | Height ≥24 px | Height ≥24 px |

- The page never widens the document; only the chart container scrolls horizontally (consistent with v1.1 §9.3).
- Touch: zoom buttons, filter controls, and drawer close meet the 44×44 px guidance from v1.1 §9.3 where practical; bars use the full row height as their target.
- No hover-only affordance carries required information; tooltips duplicate the drawer content.

### 2.6 States

| State | UI treatment |
|---|---|
| Initial load (tasks loading) | Ant Design `Skeleton` in the chart card (matching `AsyncState`). Toolbar visible but disabled. |
| Tasks request error | `ProblemAlert` with a Retry `action` button; chart still renders baseline/draft if timeline data exists; task area shows "Task bars unavailable". If no timeline either, the chart area is replaced by the alert. |
| Tasks empty, timeline present | Chart renders baseline/draft; task area shows "No tasks yet." |
| No tasks and no timeline | `Empty` state: "Nothing to plot yet." with buttons/links to Tasks and Timeline. |
| No timeline (404 `TIMELINE_NOT_FOUND`) | Info alert: "No published timeline. Task bars use their own dates." No baseline/draft rows. |
| Timeline request error (non-404) | Warning alert: "Baseline unavailable." Task chart remains fully interactive. |
| Draft only (never published) | Draft row only, labelled "Draft v1 — not active"; baseline row replaced by a one-line note. |
| Zero working days (`planWarning = NO_WORKING_DAYS`) | Warning icon/text in the baseline/draft row label and in details; no geometry change. |
| Holiday request partial/total failure | Warning naming unavailable years; weekend bands remain; holidays for successful years render. |
| Holiday horizon not covering the axis | Hatched "no holiday data" regions outside the header-reported horizon (currently `2025-01-01..2027-12-31`), plus a note in the legend. |
| Truncated task set | Info alert: "Showing the first N of M tasks." with a link to the Tasks tab. |
| Axis truncated (>3,653 days) | Info alert: "The schedule is longer than 10 years; showing the earliest 3,653 days." Bars crossing the right edge get the clipped-end chevron; the drawer still shows their true dates. |
| Invalid date in a response | The affected bar is skipped; info alert: "N entries had invalid dates and were not plotted." The rest of the chart renders normally. |
| Filters match nothing | Task rows show "No tasks match these filters."; axis and baseline remain. |
| Archived project | `ProjectLayout` already renders the archived warning. The Gantt page itself has no controls that mutate; it adds the text "Read-only view." The chart renders normally. |
| Stale overnight "today" | The marker is recomputed on every render from `jakartaToday()`. There is no timer; a normal navigation or interaction after midnight refreshes it. Documented, not a bug. |

### 2.7 Details drawer content

| Selection | Fields |
|---|---|
| Task | Title; status (`StatusTag`); PIC name; start date; end date; `durationDays` with caption "calendar days, inclusive"; `percentComplete` with `PercentBar`; project code; link "Open Tasks tab". |
| Baseline (current published) | Version badge `vN`; lifecycle `PUBLISHED`; operational status; start/end; `durationWorkdays` with caption "excludes weekends and Indonesian holidays"; `percentPlan` and `planAsOfDate`; `percentActual`; variance via `gapLabel(actual, plan)`; `planWarning` when present; revision notes; link "Open Timeline tab". |
| Draft | Version badge `vN`; label "Draft — not active"; start/end; `durationWorkdays`; `planWarning` when present; revision notes; text "Publishing is done on the Timeline tab."; link "Open Timeline tab". No actual/plan progress is shown for a draft, because DRAFT rows store OPEN/0 and those values are not operational (v1.1 §6.4.1, §8.5.5). |

The drawer is read-only. It contains no form, no save button, and no confirmation dialog.

### 2.8 Accessibility

- Chart container: `role="group"`, `aria-label="Project Gantt chart, read-only"`. Header ticks and shading bands are `aria-hidden="true"` (decorative); the same information is available in the drawer and the text alternative.
- Every bar is a native `<button type="button">` with `aria-haspopup="dialog"` and an accessible name built from its data, for example:
  - `Task Design database schema, 2 Nov 2026 to 6 Nov 2026, 5 calendar days, In progress, 40 percent complete.`
  - `Published baseline version 3, 2 Nov 2026 to 15 Apr 2027, 106 working days, 30.19 percent plan, 35 percent actual.`
  - `Draft version 4, 2 Nov 2026 to 20 Apr 2027, not active.`
- Roving tabindex: exactly one bar is in the tab order. `ArrowRight`/`ArrowLeft` and `ArrowDown`/`ArrowUp` both move to the next/previous bar in row order (top to bottom); `Home`/`End` go to the first/last bar; `Enter`/`Space` opens details; `Escape` closes the drawer and returns focus to the originating bar. Because each task has its own row, row order and visual order are the same.
- Focus ring: 2 px outline with 2 px offset, never removed.
- Colour independence: the label column shows status as text (`StatusTag`) and progress as a number (`40%`); baseline uses a solid border and the word "Baseline"; draft uses a dashed border and the words "Draft — not active"; holiday types are named in the legend and in `title`/accessible descriptions. Bar fill colour is never the only signal.
- A visually hidden `<table>` after the chart provides the complete text alternative: title, start, end, duration, status, percent, PIC for every filtered task, plus one row each for the baseline and draft when present. It is not `aria-hidden`.
- A second visually hidden list names every loaded holiday in the axis (date, name, `NATIONAL`/`JOINT_LEAVE`), so holiday information does not depend on seeing colour or hovering a band.
- Contrast targets WCAG 2.1 AA. Shading uses low-opacity fills that do not reduce text contrast in the label column.
- No animation is required; any transition respects `prefers-reduced-motion`.

### 2.9 Large-dataset behaviour

| Concern | Behaviour |
|---|---|
| Many tasks | Cap at 500 loaded tasks (§3.2). One row per task, ordered by start date, so every task name is visible in the sticky column; 500 rows at 30 px is ≈15,000 px of scrollable content. Bars beyond the cap are not loaded; a truncation notice links to Tasks. |
| Many labels | Task titles are confined to the sticky label column with ellipsis and a `Tooltip`; no labels are drawn inside bars, so labels never collide with each other. |
| Long axis | Axis capped at 3,653 calendar days (§4.3). Longer project/task extents are truncated at the right edge with a notice. |
| Many header cells | Header ticks are generated only for the visible window plus overscan. |
| Many bands | One weekend band per weekend (≈156 for three years) rather than one element per day; one holiday band per holiday date (expected ≤100/year, design v1.1 §10.6). |
| Vertical size | Chart viewport is capped and scrolls; rows are not vertically virtualised in v1 (≤500 rows × 2 DOM nodes is acceptable). If profiling shows a problem, vertical windowing is a follow-up without contract change. |

---

## 3. Data composition

### 3.1 Sources and ownership

| Data | Hook/function | Endpoint | Notes |
|---|---|---|---|
| Project | `useProjectContext()` from `ProjectLayout` | already fetched by `useProject(projectId)` | No new request from the Gantt page. |
| Timeline aggregate | existing `useTimeline(projectId)` | `GET /api/v1/projects/{projectId}/timeline` | `getTimeline` already maps `404 TIMELINE_NOT_FOUND` to `null`; that behaviour is reused. |
| All tasks (capped) | new `useGanttTasks(projectId)` | `GET /api/v1/projects/{projectId}/tasks` | Sequential pages, `size=100`, `sort=startDate,asc`. |
| Holiday years | new `useGanttHolidays(years)` | `GET /api/v1/holidays?year=YYYY&page=N&size=100&sort=date,asc` | One page loop per year, only years inside the axis and (when known) the coverage horizon. |
| Horizon | existing `useHolidayHorizon()` | `GET /api/v1/holidays?page=0&size=1&sort=date,asc` | Reads `X-Holiday-Coverage-Start/End`; used for the "no holiday data" hatch and to skip impossible years. |
| PIC filter options | derived from the loaded tasks | none | Unique `pic.userId`/`pic.name` pairs from the task summaries; covers every loaded task, including tasks whose PIC is no longer a member. Avoids an extra members request and the 100-member page limit of `useProjectMembers`. |

No cross-service call is introduced. The page still talks only to task-service and timeline-service through the existing Axios instances (`taskApi`, `timelineApi`); project data comes from user-service via the existing layout query.

### 3.2 Task composition (exact requests)

Base request, repeated:

```http
GET /api/v1/projects/{projectId}/tasks?page={n}&size=100&sort=startDate,asc
Authorization: Bearer <access token>
```

Why these parameters:

- `size=100` is the documented maximum (`PageQuery.MAX_SIZE = 100`; design v1.1 §7.4). Larger values return `400 VALIDATION_ERROR`, so 100 is the correct page size.
- `sort=startDate,asc` is in the task allow-list (`TaskService.SORT_FIELDS`: `title`, `startDate`, `endDate`, `durationDays`, `status`, `percentComplete`, `createdAt`, `updatedAt`). It matches the chart's primary order.
- No `status`, `picUserId`, or `q` filter is sent. The chart needs the full set so filters can be applied without a network round trip and without changing the axis; the toolbar is client-side.

Loader contract:

```ts
const GANTT_TASK_PAGE_SIZE = 100;   // backend maximum
const GANTT_TASK_MAX_PAGES = 5;     // hard cap: 500 tasks
const GANTT_TASK_CAP = GANTT_TASK_PAGE_SIZE * GANTT_TASK_MAX_PAGES;

interface GanttTaskResult {
  tasks: TaskSummary[];   // de-duplicated, client-sorted
  totalElements: number;  // from the first page
  truncated: boolean;
}

async function loadGanttTasks(projectId: string): Promise<GanttTaskResult>
```

Algorithm:

1. `page = 0`, `seen = new Map<string, TaskSummary>()`, `totalElements = 0`.
2. Request the page with `listTasks(projectId, { page, size: 100, sort: 'startDate,asc' })`.
3. On the first page, record `totalElements`.
4. Insert each row into `seen` keyed by `id`; the first occurrence wins.
5. Stop when `response.last === true`, when `response.content.length === 0`, when `page + 1 >= GANTT_TASK_MAX_PAGES`, or when `seen.size >= GANTT_TASK_CAP`.
6. Otherwise increment `page` and repeat sequentially. Pages are **not** fetched in parallel: sequential paging avoids overlapping mutations and keeps the loaded snapshot as coherent as the API allows.
7. `truncated = totalElements > seen.size`.
8. Return values sorted by `startDate` ascending, then `title` by code-unit comparison, then `id` (deterministic, locale-independent).

Failure policy: if any page request fails, `loadGanttTasks` rejects and the whole tasks query is in error. Partial pages are **not** shown, because a silently incomplete chart is worse than a retry prompt; the error alert offers Retry and the chart can still show the timeline baseline/draft (§3.7).

Determinism caveat (factual): the task list's sort allow-list does **not** include `id` and `TaskService.list` calls `toPageable(SORT_FIELDS, "createdAt,desc")` without the stable tie-breaker overload of `PageQuery`. Therefore the API does not guarantee a unique total ordering when many tasks share a `startDate`. This feature mitigates that by (a) loading pages sequentially inside one mount, (b) de-duplicating by id, and (c) treating the result as a best-effort snapshot; a task can theoretically be missed if rows shift between page requests. Fixing the ordering requires a backend change (adding a tie-breaker), which is explicitly out of scope for GANTT-1 and must not be done here. The truncation and "refreshed view" behaviour is documented as risk R3 (§8).

### 3.3 Timeline composition

```http
GET /api/v1/projects/{projectId}/timeline
```

The existing hook is reused unchanged. Mapping:

| Aggregate field | Chart use |
|---|---|
| `current` | Baseline row. `null` means no published version. |
| `draft` | Draft overlay row. `null` means no draft. |
| `versionCount` | Not displayed in v1; the version number comes from the version object. |
| `current.startDate/endDate` | Baseline bar geometry, axis extent. |
| `current.percentActual` | Baseline progress fill. |
| `current.percentPlan` + `planAsOfDate` | Plan marker position and details text. The chart does **not** map `percentPlan` to a fill width. |
| `current.durationWorkdays` | Details only. |
| `current.planWarning` | Warning badge when `NO_WORKING_DAYS`. |
| `draft.startDate/endDate` | Draft bar geometry, axis extent. |
| `draft.durationWorkdays`, `revisionNotes`, `planWarning` | Details only. |
| `draft.percentActual/percentPlan` | Deliberately not rendered: draft rows store `OPEN`/`0` (v1.1 §6.4.1, §8.5.5). |

Why the plan percentage is a marker, not a fill: `percentPlan` is elapsed **working** days divided by total working days (v1.1 §6.6). Mapping that percentage linearly across calendar width would place the edge on a date that has no business meaning when weekends and holidays are unevenly distributed. The chart therefore draws a 2 px vertical marker at the end of `planAsOfDate` (x = 0 when the date precedes the baseline; omitted when it is on/after the end, where the right edge already means 100 %) and shows the number in the row label and details. Actual progress remains a conventional linear fill because it is a completion percentage with no date semantics.

Live consequence: for the current version the server computes `planAsOfDate` as today in Asia/Jakarta (v1.1 §6.6), so the plan marker normally sits at the right edge of today's cell, one cell to the right of the today line (which marks the start of today's cell). The chart draws the returned date and never substitutes `jakartaToday()` for it; the percentage stays in the label and details. The marker matters most for fixtures and for a response whose as-of is not today, for example a cached response read after midnight, which the next refetch corrects.

Immutability of the baseline: a published version's planned dates and revision notes are immutable; only the current published version's operational status and actual completion can change (v1.1 §6.4.1). The chart relies on that: planned geometry for `current` is stable for the life of the published version, while `percentActual` and `operationalStatus` are re-read through the existing `['projects', projectId, 'timeline']` query, which timeline mutations already invalidate (§3.5). The Gantt never issues a timeline write, so it cannot break the immutable-version rule.

### 3.4 Holiday composition (exact requests)

For each year `Y` in the axis range, when `Y` intersects the known horizon (or when the horizon is unavailable):

```http
GET /api/v1/holidays?year=2026&page=0&size=100&sort=date,asc
```

Facts this design relies on:

- `HolidayController.list` defaults `size` to **100** when the parameter is absent, and `PageQuery` still caps it at 100.
- `year` is optional; when absent the backend uses the current year in Asia/Jakarta. The Gantt always sends it explicitly.
- The response is the standard page envelope and carries `X-Holiday-Coverage-Start` / `X-Holiday-Coverage-End`.
- The holiday sort allow-list contains `holidayDate`, `name`, `type`, `createdAt`; `HolidayService.list` rewrites a leading `date` in the sort expression to `holidayDate`, so `sort=date,asc` is accepted.
- The existing `isCoveredDate` helper hard-codes `2025-01-01..2027-12-31`; the Gantt does **not** use it. It uses the `X-Holiday-Coverage-*` headers so the hatch follows the database horizon when an admin adds a year.

Loader:

```ts
const GANTT_HOLIDAY_PAGE_SIZE = 100;
const GANTT_HOLIDAY_MAX_PAGES_PER_YEAR = 5; // defensive; seed is well under 100/year

async function loadHolidayYear(year: number): Promise<Holiday[]> {
  // sequential pages, stop at last / empty / page cap; de-duplicate by date
}
```

The years are the calendar years touched by the axis:

```ts
years = range(yearOf(axis.startIndex), yearOf(axis.endIndex))  // at most 11
```

If the horizon is known (the `useHolidayHorizon` request succeeded **and** both dates are present), years outside `[horizonStartYear, horizonEndYear]` are **not requested**: the data is known to be absent and the chart draws the "no holiday data" hatch there instead. If the intersection is empty (the whole axis lies outside the horizon), no holiday request is issued and the hook still resolves to an empty calendar so the hatch can be drawn. If the horizon request fails or returns no headers, the loader requests all axis years (still capped at 11) and the hatch is omitted.

Partial failure is tolerated: `Promise.allSettled` per year. The hook returns the following shape (the `HolidayCalendar` type is exported from `src/lib/gantt/model.ts` so the pure model does not depend on the features layer):

```ts
interface HolidayCalendar {
  byDate: Map<string, Holiday>;  // keyed by ISO date string, no parsing
  failedYears: number[];         // years whose request failed
  loadedYears: number[];
}
```

A holiday is identified by its ISO `date` string; membership tests use the string set/map. No `Date` parsing is involved.

### 3.5 Query keys, caching, invalidation

New keys are added to `src/lib/queryKeys.ts`:

```ts
gantt: {
  tasks: (projectId: string) => ['projects', projectId, 'tasks', 'gantt'] as const,
  holidays: (years: number[]) => ['holidays', 'gantt', years] as const,
}
```

| Query | Key | Invalidated by (existing behaviour) |
|---|---|---|
| Gantt tasks | `['projects', projectId, 'tasks', 'gantt']` | Task mutations call `invalidateQueries({ queryKey: ['projects', projectId, 'tasks'] })`; prefix matching invalidates this key. |
| Timeline aggregate | `['projects', projectId, 'timeline']` (existing) | Timeline mutations call `invalidateQueries({ queryKey: ['projects', projectId, 'timeline'] })`. |
| Holidays | `['holidays', 'gantt', years]` | Holiday mutations call `invalidateQueries({ queryKey: ['holidays'] })`. |
| Horizon | `['holidays', 'horizon']` (existing) | Same holiday prefix. |

Caching defaults are inherited from `main.tsx`: `staleTime: 20_000`, one retry for 5xx/network, no retry for 4xx, `refetchOnWindowFocus: false`. No Gantt-specific override is required. Mutations elsewhere in the app therefore refresh the chart when the user returns to it after the cache is stale or invalidated.

`years` is computed from the axis, sorted ascending, de-duplicated, and restricted to years that intersect the known horizon before the key is formed, so the same axis always maps to the same cache entry and array identity does not cause churn. The page passes `null` until both the axis is known and the horizon query has settled (`enabled: years !== null`), which means "not settled yet". Once both have settled the page passes an array, which is empty when no axis year intersects the horizon; an empty array issues no request and resolves immediately to an empty `HolidayCalendar`. That distinction matters for the uncovered-horizon hatch (§4.6): the hatch must draw when the calendar has settled with zero rows, but must not draw while loading.

Why not reuse `useHolidays(year, page, size)` for year-wide loading: its query key is `['holidays', year, page]` and **does not include `size`**. Reusing it with `size=100` could collide with the Holidays page's `size=20` cache entry for the same year/page, serving 20 rows to the chart. The Gantt loader therefore has its own key and function. No change is made to the Holidays page or its hook.

### 3.6 Request budget and N+1 control

| Scenario | Requests |
|---|---|
| Typical 3-year project, ≤100 tasks, 3 holiday years | 1 project (layout) + 1 tasks + 1 timeline + 3 holidays + 1 horizon = 7 |
| 480 tasks | 1 + 5 tasks + 1 + Y + 1 |
| 10-year axis, no horizon knowledge | 1 + 5 tasks + 1 + 11 holidays + 1 = 19 worst case |

Rules:

- Task pages are capped at 5 and loaded sequentially.
- Holiday pages are capped at 5 per year and loaded per year; years are capped at 11 by the axis cap (a 3,653-day window can touch 2020-12-31..2030-12-31, i.e. 11 calendar years).
- No request depends on an individual task or row; there is no per-row fetch. The loaded task summary already includes PIC name and project id, so no user or project lookups are needed per task (v1.1 §5.5 `pic_name` snapshot).
- Holiday years are only requested after the axis is known (tasks and timeline settled) and the horizon query has settled (success or failure): the page does not over-fetch when the project has no data, and no duplicate holiday requests are issued when the horizon headers arrive.

### 3.7 Loading and error independence matrix

| Failing/slow source | Chart result | User feedback |
|---|---|---|
| Project detail | `ProjectLayout` owns the error; Gantt page is not rendered. | Existing `AsyncState` retry in the layout. |
| Tasks (loading) | Skeleton | — |
| Tasks (error) | Baseline/draft still render; no task bars | `ProblemAlert` + Retry |
| Timeline (loading) | Task chart renders; baseline slot shows a small skeleton | — |
| Timeline (404) | Task chart, no baseline/draft | Info alert |
| Timeline (error) | Task chart, no baseline/draft | Warning alert; no retry blocking the page (retry button optional) |
| Holidays (loading) | Task/baseline chart renders without holiday bands | "Loading holiday shading…" small text |
| Holidays (partial) | Successful years shaded | Warning listing failed years |
| Holidays (all fail) | Weekend shading only | Warning |
| Horizon (error) | No hatch, all axis years requested | No blocking message |

Only the tasks query gates the main chart skeleton. Every other query is non-blocking.

---

## 4. Chart model and algorithms

All functions in this section are pure, live under `src/lib/gantt/`, and are unit-tested without React or the DOM.

### 4.1 Date-only rules (normative)

**Rule D1 — no string date arithmetic.** ISO date strings may be compared lexicographically (`'2026-01-02' < '2026-11-02'`) because they are fixed-width and zero-padded, but they are never passed to `new Date(string)`.

**Rule D2 — integer day ordinals.** All arithmetic converts a civil date to an integer:

```ts
dayIndex = Math.floor(Date.UTC(year, month - 1, day) / 86_400_000)
```

`Date.UTC` has no time zone and no DST, so differences are exact calendar days on every machine. This is **calendar-day arithmetic** (civil year/month/day components), not local-time instant arithmetic: UTC is used only as a zone-free container for the ordinal, so no local constructor, DST transition, or browser offset can enter the calculation. One caveat: `Date.UTC` maps a two-digit year 0–99 to 1900–1999. `toDayIndex` therefore rejects `year < 100` (and years outside `0001..9999`) before calling it; the Gantt domain never needs such years, but the guard keeps the helper total and testable.

**Rule D3 — validated parsing.** `parseIsoDate` accepts `/^(\d{4})-(\d{2})-(\d{2})$/`, builds the ordinal, and verifies the round-trip (`civilFromIndex(index)` equals the input components). `2026-02-30` is rejected rather than silently normalised to 2 March.

**Rule D4 — no local constructors in chart math.** Chart code must not use `new Date(y, m, d)`, `new Date('YYYY-MM-DD')`, `dayjs('YYYY-MM-DD').diff(...)`, or `date.getDate()`/`getDay()` in local time. It uses `Date.UTC` plus `getUTCDay()`, `getUTCFullYear()`, etc. `new Date(milliseconds)` is permitted only inside `civilFromIndex`, where the result is immediately read back with `getUTC*` and never formatted or compared locally.

**Rule D5 — today is Jakarta today.** "Today" comes only from the existing `jakartaToday()` in `src/lib/dates.ts` (`dayjs().tz('Asia/Jakarta').format('YYYY-MM-DD')`), then through the same ordinal conversion. The browser's local date is never used.

**Rule D6 — display is separate from arithmetic.** Existing `formatDate(value)` may be used for display because it formats a dayjs value without day arithmetic; it is not used to compute positions. Details and aria labels use `formatDate`; geometry uses ordinals. The existing `daysBetweenInclusive` helper in `src/lib/dates.ts` uses a local-time dayjs diff and is therefore **not** used for chart geometry; it stays untouched for the pages that already use it.

Consequence: a user in UTC−8, UTC, or UTC+14 sees the same bars on the same calendar dates, and the today marker follows Jakarta's date.

### 4.2 Coordinate system

```text
index = integer days since 1970-01-01 (UTC-based)
axis  = { startIndex, endIndex, dayCount }   // inclusive
x(index) = (index - axis.startIndex) * pxPerDay
width(start, end) = (endIndex - startIndex + 1) * pxPerDay
```

- Bars occupy whole day cells. There is no `-1 px` adjustment: a task from 2 Nov to 6 Nov occupies five cells, and its right edge is exactly the start of 7 Nov.
- A single-day task is exactly `pxPerDay` wide.
- All three bar kinds (task, baseline, draft) use calendar days. Working days affect only shading and the server-provided duration text.
- `x` values are fractional only if `pxPerDay` is fractional; the chosen values are integers, so positions are integers.

### 4.3 Axis domain

Inputs: project period (always present), all **loaded and unfiltered** tasks, `current`, `draft`.

```ts
interface AxisInput {
  project: { startDate: string; endDate: string };
  tasks: TaskSummary[];              // unfiltered
  current: TimelineVersion | null;
  draft: TimelineVersion | null;
}

interface Axis {
  startIndex: number;
  endIndex: number;
  dayCount: number;                  // endIndex - startIndex + 1
  truncated: boolean;
  invalidDateCount: number;
}
```

Algorithm:

1. Collect valid ranges from project, tasks, current, draft. A range with an invalid date is skipped and counted in `invalidDateCount`.
2. `start = min(starts)`, `end = max(ends)`. The project range is validated and always present, so `start` and `end` are defined and `start <= end`; no defensive "reversed range" branch is needed.
3. Guarantee a minimum span of 7 days: `end = max(end, start + 6)`. This avoids a degenerate axis for a one-day project.
4. Cap at `MAX_AXIS_DAYS = 3653`: 3,653 is the longest inclusive count of any 10-calendar-year window (it includes leap days and one boundary day, e.g. 2020-12-31..2030-12-31), so it comfortably contains the maximum task range of 3,650 inclusive days. If `end - start + 1 > 3653`, set `end = start + 3652` and `truncated = true`.
5. The axis starts at `start`; there is no leading padding.

Why the union rather than only the project period: project dates are descriptive boundaries and do not constrain task or timeline dates (v1.1 §6.8: "Project dates do not drive task or timeline calculations"). A task outside the project period must still be visible; otherwise the chart would silently hide real work. The union also makes the published baseline visible even if it extends past the project period (timeline dates are constrained by the holiday horizon, not by project dates).

Why truncate at the right edge: keeping the earliest date is the more useful default for a schedule; a notice explains the truncation and the true dates remain available in the Task details. Truncation is rare; it requires either a project longer than 10 years or tasks spread over more than 10 years.

### 4.4 Task ordering and row assignment

Ordering (deterministic, locale-independent):

```ts
compareTasks(a, b) =
  dayIndex(a.startDate) - dayIndex(b.startDate)
  || compareCodeUnits(a.title, b.title)
  || compareCodeUnits(a.id, b.id)
```

`compareCodeUnits` compares UTF-16 code units (`a < b ? -1 : a > b ? 1 : 0`), not `localeCompare`, so ordering is identical in every runtime and test environment. Tasks with an invalid date are excluded before ordering and counted in `Axis.invalidDateCount`.

Row policy: **one row per task**, in `compareTasks` order.

```ts
interface TaskRowModel {
  task: TaskSummary;
  row: number;      // 0-based position in the sorted, filtered list
  bar: BarRect;
}

function buildTaskRows(
  tasks: TaskSummary[],
  axis: Axis,
  filters: GanttFilters,
  pxPerDay: number,
): TaskRowModel[]
```

Algorithm:

1. Drop tasks whose `startDate` or `endDate` is not a valid ISO date or whose end precedes start; `buildAxis` has already counted them in `invalidDateCount`.
2. Filter with `filterTasks` (§4.4.1).
3. Sort the filtered list with `compareTasks`.
4. Map each task to `row = index` and compute its `bar` with `barRect` (§4.5).
5. `rowCount = rows.length`; `buildChartModel` sets the rows height to `rowCount * ZOOM_CONFIG[zoom].rowHeight` (30 px at day/week, 24 px at month, §2.5).

Why not interval packing: a lane can hold several sequential tasks, but the sticky label column has exactly one label per row. Packing would leave every task in a lane except the first without a visible name, which breaks the primary "task names stay visible" affordance (US-6) and would force in-bar labels that the design deliberately avoids (§4.9). One row per task keeps label, bar, drawer selection, and aria label one-to-one. The cost is height: 500 tasks × 30 px ≈ 15,000 px inside the capped scroll viewport, which the 500-task cap bounds (§3.2, S2). Vertical windowing is a possible later optimisation that does not change this row contract.

Filtering is applied **before** sorting and row assignment. Changing filters therefore renumbers rows (a task's row index can change), but the axis domain does **not** change (AC-11).

#### 4.4.1 Filtering

`filterTasks(tasks, filters)` keeps a task when all active predicates match; an empty predicate is "no constraint":

- `statuses`: empty array = all; otherwise `statuses.includes(task.status)`.
- `picUserId`: `null` = all; otherwise `task.pic.userId === picUserId`.
- `search`: trimmed, case-insensitive substring match on `task.title` using `String.prototype.toLowerCase()` (locale-independent), not locale-aware folding; empty string = all.

The function is pure and does not mutate its input. PIC options are derived from the unfiltered loaded tasks (§3.1), so selecting one PIC does not remove the other options from the dropdown. When the task set is truncated (§3.2), a PIC who appears only beyond the cap has no option; the title search still reaches the loaded tasks and the truncation notice explains the limit.

### 4.5 Bar geometry

```ts
interface BarRect {
  x: number;            // px from axis start
  fullWidth: number;    // px, actual inclusive span before clipping
  width: number;        // px, visible (clipped) span; equals fullWidth unless the right edge is truncated
  visualWidth: number;  // max(width, MIN_BAR_PX) for rendering
  clippedEnd: boolean;
  progressWidth: number; // min(fullWidth * percent / 100, width), 0 when no progress
}
```

- `x = (startIndex - axis.startIndex) * pxPerDay`.
- `fullWidth = daySpanInclusive(startIndex, endIndex) * pxPerDay`.
- `width` is `fullWidth` clipped to `[0, axis.dayCount * pxPerDay - x]`. If the clipped width is 0, the bar is not rendered. A bar whose true end is past the right edge gets `clippedEnd = true` and a CSS chevron (`is-clipped-end`) so the user knows the true range continues beyond the axis. Left clipping cannot occur because the axis starts at the earliest valid range; only right-edge truncation exists (§4.3).
- `visualWidth = max(width, MIN_BAR_PX)`; constants per zoom: day 6, week 4, month 4 px. A one-day task at month zoom is 3 px actual but 4 px visual; the extra pixel is cosmetic and is not used in date calculations. The chevron/hit logic still uses the actual range. Because each task has its own row, the extra pixel can only overlap a grid line, never another bar.
- Progress fill is a percentage of the **actual full width** (`fullWidth * percent / 100`), rendered as an explicit pixel width and then clipped to the visible `width`. It is not `100%` of the visual bar: at month zoom a one-day bar is 3 px actual but 4 px visual, so a 40 % fill of the visual bar would be 1.6 px (53 % of the true 3 px span), whereas the correct fill is 1.2 px. Computing from `fullWidth` also keeps progress honest when a truncated bar is only partly visible: a 50 % task that is cut at the right edge does not suddenly read as 100 % of its visible stub.
- Percent values are clamped to `[0, 100]` and non-finite values are treated as 0.
- Task bar kind = `task`; baseline kind = `baseline`; draft kind = `draft`.
- Baseline progress uses `percentActual`. Draft progress is not drawn.
- The baseline plan marker is drawn whenever `planAsOfDate` is a valid date **before** `endDate`; its x is `(planIndex - startIndex + 1) * pxPerDay` relative to the bar start (end of the as-of day), clamped to `[0, width]`. If `planAsOfDate` is before `startDate` (server reports `percentPlan = 0.00`), the marker sits at `x = 0`, which is meaningful ("nothing elapsed yet"). If it is on or after `endDate` (server reports `percentPlan = 100.00`), no marker is drawn because the bar's right edge already communicates the same position. A 2 px line with a contrasting outline is used. It is decorative; the number is in the row label and details.

### 4.6 Bands: weekends, holidays, uncovered horizon

```ts
interface Band {
  kind: 'weekend' | 'holiday-national' | 'holiday-joint' | 'no-holiday-data';
  x: number;
  width: number;
  label?: string;   // holiday name; used for the `title` tooltip (names are also in the hidden holiday list)
}
```

Weekend bands:

- Iterate Saturdays from `axis.startIndex` to `axis.endIndex`.
- For each Saturday `s`, band start `x(s)`, width `2 * pxPerDay`, clipped at the axis end. If the axis ends on the Saturday, width is `pxPerDay`.
- Sunday-only start: the axis may start on a Sunday. The Saturday preceding the axis is outside; render a band at `x(axis.startIndex)` of width `pxPerDay` for that Sunday. Implementation handles this by iterating days once and coalescing consecutive Saturday/Sunday indices, which naturally yields the correct clipped bands. Complexity O(dayCount) at most 3,653 operations, producing ≈156 bands for three years.

Holiday bands:

- For each holiday whose `date` parses inside the axis, one band of width `pxPerDay`, kind by `type`.
- Holidays falling on Saturday/Sunday are still drawn (the backend allows them); they paint over the weekend band with the holiday colour and remain in the legend.
- Duplicate dates cannot occur (`holiday_date` unique), but the loader de-duplicates by date defensively.

Uncovered horizon:

- If the horizon is known and the calendar request has settled (`holidays !== null`, including the empty calendar produced when no year intersects the horizon), and the axis extends before `horizonStart` or after `horizonEnd`, add `no-holiday-data` bands for the uncovered ranges (clipped to the axis). They render as a subtle diagonal hatch, never as a claim that the days are working days. While holidays are still loading, no hatch is drawn (absence is not yet known).
- If the horizon is unknown (request failed or headers absent), no hatch is drawn.

Painting order (bottom to top): uncovered hatch → weekend → holiday → grid lines → bars → today marker. Holiday bands therefore take precedence over weekends, and the today marker stays visible across bars.

### 4.7 Today marker

```ts
// todayIso is injected by the page (jakartaToday()); pure code never reads the clock.
const todayIndex = toDayIndex(todayIso);
const visible = todayIndex >= axis.startIndex && todayIndex <= axis.endIndex;
const todayX = visible ? x(todayIndex) : null;
```

- The date is a parameter, not a module-level `jakartaToday()` call inside `model.ts`/`scale.ts`, so model tests can pin "today" and are not date-dependent. Only `GanttPage` calls `jakartaToday()` and passes the resulting `YYYY-MM-DD` string into `buildChartModel`.
- `todayX === null` is the single signal for "outside the axis": the chart draws nothing and the page disables the Today button.

- The line is drawn at the **start** of today's cell, with a 1 px line and a small label above the header ("Today · 22 Oct 2026").
- If today is outside the axis, no line and no label; the Today button is disabled.
- The marker is drawn above bands **and above bars** as a 1 px dashed line at 60 % opacity with `pointer-events: none`, plus a solid 2 px cap in the header, so it stays visible across opaque bars without intercepting clicks.
- It is recomputed on render; no timer.

### 4.8 Header ticks

```ts
interface Tick {
  index: number;
  x: number;
  label: string;
  showLabel: boolean;
}

interface TickGroup {
  key: string;       // e.g. '2026-11'
  label: string;     // e.g. 'November 2026' or '2026'
  x: number;
  width: number;
}
```

Generation by zoom:

| Zoom | Tick step | Tick label | Group |
|---|---|---|---|
| Day | 1 day | `DD` | Month (`MMMM YYYY`) |
| Week | 7 days, anchored on Mondays | `DD MMM` | Month |
| Month | 1st of each month | `MMM YYYY` | Year (`YYYY`) |

- The tick generator takes the visible pixel window `[scrollLeft, scrollLeft + viewportWidth]` plus one viewport of overscan and returns only ticks/groups intersecting it. It never materialises thousands of labels.
- Label thinning: `labelEvery = max(1, ceil(MIN_LABEL_PX / (pxPerDay * stepDays)))`. Constants: day `MIN_LABEL_PX = 22` (32 px/day → every day), week `56` (56 px/week → every week), month `64` (≈90 px/month → every month). A tick whose label is suppressed still draws its grid line when `showLabel` is false.
- Week ticks use Monday as the week start, regardless of locale. The weekday is computed with `getUTCDay()` and an ISO-style conversion `(utcDay + 6) % 7` (Monday = 0).
- Month and year boundaries are computed from ordinal dates, not from string slicing, so December→January and leap years are handled once and tested.
- Group widths are `(groupEndIndex - groupStartIndex) * pxPerDay`, where `groupEndIndex` is exclusive (the start of the next group), clipped to the axis. A full November is therefore 30 × pxPerDay, not 29.
- Ticks and groups are always intersected with `[0, axis.dayCount * pxPerDay]` before rendering: the first tick may start before the axis (its label is suppressed and only the in-axis part of the grid line is drawn), and the last group may be narrower than a full month/year. Negative `x` values from overscan are never passed to CSS.
- The header is rendered `aria-hidden`; the visible chart caption states the axis and zoom level.

### 4.9 Label collision avoidance

There are three label families and each has a deterministic rule:

1. **Header labels**: thinned by pixel budget (§4.8); no overlap by construction.
2. **Task labels**: only in the sticky left column, one per task row, ellipsised with a tooltip. No label is drawn inside a bar. Rows cannot overlap because each row is fixed height and contains exactly one bar.
3. **Bar-adjacent text**: progress percentage is in the label column, not on the bar. Holiday names are in `title`/drawer/legend, not floating on the chart. Therefore no collision algorithm is required inside the track.

### 4.10 Timezone-sensitive edge cases covered by construction

| Case | Handling |
|---|---|
| Single-day task | width = 1 × pxPerDay; not zero, not two days. |
| Task ending on 31 Dec and another starting 1 Jan | ordinals differ by 1; independent bars. |
| Leap day 2028-02-29 | ordinal conversion is exact; no month-length table. |
| Task entirely outside the project period | included in the union; axis extends (unless capped). |
| Axis starts on Sunday | weekend coalescing emits a one-day Sunday band. |
| Today is a weekend/holiday | marker still drawn; shading does not suppress it. |
| Holiday on a weekend | holiday band painted over weekend band. |
| `2026-02-30` in a malformed response | rejected by round-trip validation, counted, not shifted. |
| User browser in UTC−11 or UTC+14 | geometry uses UTC ordinals; today uses Jakarta. |

---

## 5. Component architecture

### 5.1 File list

New files unless marked **M** (modified).

| Path | Contents | Why here |
|---|---|---|
| `src/app/App.tsx` **M** | Add `<Route path="gantt" element={<GanttPage />} />` under the project layout. | Routing lives in the app route tree. |
| `src/app/ProjectLayout.tsx` **M** | Add `gantt` to `activeTab` detection and to the `Tabs` items. | Tab pattern is owned here. |
| `src/lib/queryKeys.ts` **M** | Add `gantt.tasks` and `gantt.holidays` keys. | Query keys are centralised in `src/lib`. |
| `src/lib/gantt/dateMath.ts` | `parseIsoDate`, `toDayIndex`, `civilFromIndex`, `formatIsoDate`, `weekday`, `addDays`, `daySpanInclusive`, `compareIso`, `monthStart`, `yearStart`, `range`. | Pure calendar math, 100 %-gated. |
| `src/lib/gantt/scale.ts` | `ZoomLevel`, `ZOOM_CONFIG`, `MAX_AXIS_DAYS`, `buildAxis`, `xForIndex`, `barRect`, `clipRange`, `clampPercent`. | Pure geometry. |
| `src/lib/gantt/ticks.ts` | `buildTicks(axis, zoom, window)`, `buildGroups`, weekday/ISO helpers for headers. | Pure tick generation. |
| `src/lib/gantt/tasks.ts` | `compareTasks`, `sortTasks`, `filterTasks`. | Pure ordering/filtering. |
| `src/lib/gantt/model.ts` | `buildChartModel` composing axis, task rows, bars, bands, row height, total width; `buildTaskRows`, `buildWeekendBands`, `buildHolidayBands`, `buildUncoveredBands`, `selectionKey`; `HolidayCalendar` type. | Pure view model. |
| `src/features/gantt/api.ts` | `GANTT_*` constants, `loadGanttTasks`, `loadHolidayYear`, `loadHolidayCalendar`, `useGanttTasks`, `useGanttHolidays`. | Server-state composition; features `.ts` are 100 %-gated. |
| `src/components/gantt/GanttChart.tsx` | Scroll container, sticky labels/header, bars, bands, today marker, keyboard navigation, text-alternative table. | Presentational chart, `components/**/*.tsx` gated at 90. |
| `src/components/gantt/GanttLegend.tsx` | Legend for task/progress/baseline/draft/weekend/national/joint/no-data. | Presentational, gated with chart tests. |
| `src/components/gantt/GanttDetailsDrawer.tsx` | Read-only drawer for task/baseline/draft selections. | Presentational. |
| `src/pages/GanttPage.tsx` | Page composition, filters, zoom, toggles, query wiring, notices. | Pages are intentionally outside the coverage gate; keep it thin. |
| `src/styles.css` **M** | `.gantt-*` classes, sticky cells, band colours, min hit target, hidden text table. | Global stylesheet convention. |
| `src/test/msw/ganttHandlers.ts` | MSW v2 handlers for tasks/timeline/holidays. | `src/test/**` is excluded from coverage; reusable by integration tests. |

Test files are listed in §7.2.

Dependency direction is one-way: `pages` → `components`/`features` → `lib/gantt` → `types`. `src/lib/gantt` must not import from `src/features`, `src/components`, or `src/pages`; shared server types come from `@/types/api`, and `HolidayCalendar` is owned by `model.ts` so the pure layer stays independent of query code.

### 5.2 Props and interfaces

```ts
// src/lib/gantt/scale.ts (re-exported by model.ts)
export type ZoomLevel = 'day' | 'week' | 'month';

// src/lib/gantt/model.ts
export type GanttSelection =
  | { kind: 'task'; task: TaskSummary }
  | { kind: 'baseline'; version: TimelineVersion }
  | { kind: 'draft'; version: TimelineVersion };

export interface ChartModel {
  axis: Axis;
  zoom: ZoomLevel;
  totalWidth: number;
  headerHeight: number;
  baseline: BaselineModel | null;
  draft: DraftModel | null;
  rows: TaskRowModel[];
  rowCount: number;
  bands: Band[];
  todayX: number | null;
  truncated: boolean;
  invalidDateCount: number;
}

export interface ChartInput {
  project: Project;
  tasks: TaskSummary[];            // unfiltered, already capped/de-duplicated
  timeline: TimelineAggregate | null;
  holidays: HolidayCalendar | null;
  filters: GanttFilters;           // statuses, picUserId, search
  zoom: ZoomLevel;
  todayIso: string;                // from jakartaToday(), injected for testability
  horizon: { startDate: string | null; endDate: string | null } | null; // structurally compatible with HolidayHorizon
  showDraft: boolean;              // view toggle; hides the row without changing the axis
  showHolidays: boolean;           // view toggle; hides holiday bands, keeps weekends/hatch
}

export interface GanttFilters {
  statuses: TaskStatus[];          // empty = all
  picUserId: string | null;
  search: string;                  // debounced value
}
```

`buildChartModel` always builds the axis from the unfiltered tasks plus `current` and `draft`; `showDraft` and `showHolidays` only remove output rows/bands, so toggling a view option never changes the axis or refetches.

```ts
// Supporting model types (same file)
export interface TaskRowModel {
  task: TaskSummary;
  row: number;
  bar: BarRect;
}

export interface HolidayCalendar {
  byDate: Map<string, Holiday>;   // keyed by ISO date string, no parsing
  failedYears: number[];
  loadedYears: number[];
}

export interface BaselineModel {
  version: TimelineVersion;
  bar: BarRect;               // includes progressWidth for percentActual
  planMarkerX: number | null; // null when planAsOfDate is on/after endDate or invalid
}

export interface DraftModel {
  version: TimelineVersion;
  bar: BarRect;
  inactive: true;             // draft has no operational progress
}
```

```ts
// src/components/gantt/GanttChart.tsx
export interface GanttChartProps {
  model: ChartModel;
  selectedKey: string | null;
  onSelect: (selection: GanttSelection | null) => void;
  scrollTarget: { x: number; token: number } | null;
}
```

```ts
// src/components/gantt/GanttDetailsDrawer.tsx
export interface GanttDetailsDrawerProps {
  selection: GanttSelection | null;
  open: boolean;
  projectId: string;
  projectCode: string;
  onClose: () => void;
}
```

```ts
// src/features/gantt/api.ts (imports HolidayCalendar from '@/lib/gantt/model')
export interface GanttTaskResult { tasks: TaskSummary[]; totalElements: number; truncated: boolean; }
export function useGanttTasks(projectId: string): UseQueryResult<GanttTaskResult>;
export function useGanttHolidays(years: number[] | null): UseQueryResult<HolidayCalendar>;
```

### 5.3 State ownership

| State | Owner | Rationale |
|---|---|---|
| `statuses`, `picUserId`, `search` | `GanttPage` (`useState`), search debounced with existing `useDebouncedValue` | Page-level user input; mirrors `TasksPage`. |
| `zoom` | `GanttPage`, default `week` | Toolbar control; passed to model. |
| `showDraft`, `showHolidays` | `GanttPage`, both default `true` | View options passed to `buildChartModel`; do not refetch and do not change the axis. |
| `selection` | `GanttPage` | Drives the drawer and selected-bar styling. |
| `scrollTarget` | `GanttPage` (`{ x, token }`) | "Today" action scrolls to `model.todayX`; the token forces the effect even when `x` is unchanged. |
| `scrollLeft`, `viewportWidth`, roving focus index | `GanttChart` | Rendering-only concerns; avoids page re-renders on scroll. |
| Task/holiday/timeline/project data | TanStack Query cache | Server state; existing conventions. |
| Derived chart model | `useMemo` in `GanttPage` from queries + filters + zoom + `jakartaToday()` | Pure and cheap (≤500 tasks). |

No global store, context, or URL state is added.

### 5.4 Coverage-gate implications

`vite.config.ts` includes only:

- `src/lib/**/*.ts`
- `src/auth/**/*.{ts,tsx}`
- `src/features/**/*.ts`
- `src/components/**/*.tsx`

and excludes test files, `src/test/**`, `src/types/**`, and `src/features/**/*Drawer.tsx`. Thresholds:

- global 95 lines / 95 functions / 95 statements / 85 branches;
- `src/lib/**` and `src/features/**` 100 / 100 / 100 / 90;
- `src/components/**` 90 / 90 / 90 / 80.

Consequences, deliberately chosen:

- Chart math goes in `src/lib/gantt` and is therefore held to 100 % lines/functions/statements and 90 % branches. This is desirable: formulas and edge cases must be tested.
- Query composition goes in `src/features/gantt/api.ts` (`.ts`, included) and is held to 100/100/100/90.
- Presentational chart code goes in `src/components/gantt/*.tsx`, held to 90/90/90/80.
- `GanttPage.tsx` is a `.tsx` under `src/pages`, which is **not** in the include list. It is intentionally thin: no geometry, no packing, no request construction. Page tests still exercise it, but the gate does not depend on it.
- Do **not** place chart components under `src/features/gantt/*.tsx`: `src/features/**/*.ts` includes only `.ts`, so a component there would be effectively ungated. That would weaken the approved coverage model.
- Do **not** add new business logic to `src/pages` to avoid tests. A reviewer can check this by file placement and imports.
- New pure modules must avoid unreachable defensive branches; every branch needs a test (90 % branch minimum, aiming for 100 %).

---

## 6. Rendering approach

### 6.1 Options considered

| Option | Licence | Strengths | Problems for this feature |
|---|---|---|---|
| Custom DOM/CSS (recommended) | In-repo code | Exact control of baseline/draft overlays and Indonesian shading; native buttons for accessibility; testable pure geometry; no bundle growth; no library upgrade risk. | We own edge cases (axis, rows, ticks) and must test them. Estimated one chart component plus ~5 pure modules. |
| Frappe Gantt | MIT | Small, SVG, day/week/month views, pleasant defaults. | Task-centric model; no baseline overlay or holiday shading; built around drag interactions that must be disabled; styling/accessibility customisation would be substantial. |
| DHTMLX Gantt | GPL v2 for open source; commercial otherwise | Very complete, baselines, markers, calendars. | Licence choice affects distribution; large bundle; its calendar model would duplicate and potentially contradict the server's working-day rules; read-only mode disables most of what is paid for. |
| Bryntum Gantt | Commercial | Excellent baseline/dependency support. | Commercial licence and bundle size are disproportionate for one read-only view. |
| vis-timeline | Apache-2.0 | Timeline primitives, ranges, markers. | Generic timeline rather than project Gantt; still needs custom task/baseline rows and holiday shading; adds a dependency for geometry we can implement in ~400 lines. |
| `gantt-task-react` | MIT | React-native API, simple. | Limited baseline/holiday support; opinionated styling; less control over date-only semantics and accessibility. |

Licence statements are as published by each project at the time of writing; any adoption would require a fresh licence/version check. This document does not add a dependency, so no such check is on the critical path.

### 6.2 Recommendation

**Build a custom DOM/CSS chart. Do not add a Gantt library.**

Reasons, in priority order:

1. **Read-only scope.** The main value of Gantt libraries is interactive editing (drag, resize, dependency drawing, resource assignment). All of it would be disabled, leaving only layout—the part we can implement precisely.
2. **Contract fidelity.** The baseline is a versioned timeline aggregate, and the draft is a non-operational overlay. Mapping those two server objects into a library's task/baseline model risks semantic drift (especially `percentPlan`, which must not be turned into a calendar fill). A custom model maps fields one-to-one and displays plan as a marker at `planAsOfDate`.
3. **Holiday shading.** Indonesian weekends plus two holiday types plus an uncovered-horizon hatch are first-class requirements. In libraries this is usually a custom "non-working time" callback and CSS fight; here it is a pure band list.
4. **Date-only correctness.** A custom layer uses UTC ordinals and Jakarta today, with unit tests. Libraries commonly parse `YYYY-MM-DD` through local `Date` objects, which is exactly the off-by-one risk this design forbids.
5. **Accessibility and testability.** Bars are native buttons with ARIA labels, roving focus, and a text-alternative table. RTL can query roles and names. Canvas/SVG library internals are harder to test and often not keyboard-accessible.
6. **Dependencies.** No new package, no licence review, no bundle increase, no supply-chain surface. The repo already has Ant Design for Drawer/Select/Segmented/Tooltip and Day.js for display formatting.

### 6.3 DOM structure and styling

```html
<div class="gantt-scroll" role="group" aria-label="Project Gantt chart, read-only">
  <div class="gantt-canvas" style="width: totalWidth + labelWidth">
    <div class="gantt-header" style="height: headerHeight">
      <div class="gantt-corner">Task</div>
      <div class="gantt-header-track">…groups + ticks…</div>
    </div>
    <div class="gantt-body">
      <div class="gantt-bands" aria-hidden="true">
        <div class="gantt-band gantt-band--weekend" />
        <div class="gantt-band gantt-band--holiday-national" title="Nyepi" />
        …
      </div>
      <div class="gantt-today" aria-hidden="true" />
      <div class="gantt-row gantt-row--baseline">
        <div class="gantt-label">Baseline · v3 …</div>
        <div class="gantt-track">
          <button class="gantt-bar gantt-bar--baseline" aria-haspopup="dialog">…</button>
        </div>
      </div>
      …
    </div>
  </div>
</div>
```

- Bands are **not** duplicated per row. Because each row is a separate horizontal track, painting ~234 band nodes in every row would multiply DOM size by the row count. Instead the band layer is painted once as an absolutely positioned background layer behind all chart rows (baseline, draft, and task rows), spanning the rows area, and bars are positioned in their rows above it. Rows therefore have transparent tracks, keeping DOM size at ~bands + bars rather than bands × rows.
- The shared layers (`.gantt-bands`, `.gantt-today`) are children of `.gantt-body` positioned at `left: labelWidth; width: totalWidth`; they scroll with the content (they are inside the same scroll container) and are never independently scrolled. Sticky label cells paint above them with opaque backgrounds.
- Alternative, used if sticky/scroll interactions make the shared layer fragile: a `repeating-linear-gradient` for weekends plus holiday overlay per row. The shared layer is the chosen design because it is testable and exact.
- Bars are absolutely positioned inside their row track (`left: x; width: visualWidth`), with the row index controlling `top`.
- The header track is also absolutely positioned so ticks align exactly with bar x coordinates.
- CSS variables carry `--gantt-px-day`, `--gantt-row-height`, and band colours, so zoom changes are one style update, not per-bar recalculation.
- The text-alternative table uses a standard visually-hidden pattern (1 px clip), not `display:none`, so it remains in the accessibility tree.

### 6.4 Why not SVG or canvas

- SVG would make band/bar drawing concise but complicates sticky label cells, text ellipsis, focus rings, and RTL/zoom accessibility; it also tends to produce large node trees that are harder to query in RTL.
- Canvas would be performant but inaccessible without a parallel DOM, and untestable with RTL semantics. The dataset is capped at 500 bars, so DOM performance is not the constraint.

---

## 7. Test strategy

### 7.1 Principles

- Follow the existing repo style: Vitest + React Testing Library, `vi.mock` for page-level hook isolation, no snapshots, assertions on user-visible behaviour.
- Pure functions are tested directly with table-driven cases, including boundaries (single day, month/year transitions, leap day, weekend-only, no working days, out-of-range tasks).
- The Gantt page has one MSW-backed integration test that exercises the real query composition (paging, timeline 404, partial holiday failure). MSW is already a dev dependency (`msw ^2.12.3`) but is not yet wired into this repo; GANTT-1 adds the first handlers under `src/test/msw/`. The rest of the suite keeps using the existing `vi.mock` pattern.
- Do not assert implementation details such as CSS class churn or DOM order beyond what the contract requires; assert integer geometry (`x`, `width`) exactly and percentage-derived lengths (progress fill) with `toBeCloseTo`, and role/name where styling encodes meaning.

### 7.2 New test files

| File | Covers |
|---|---|
| `src/lib/gantt/dateMath.test.ts` | ISO parse round-trip; rejection of `2026-02-30`, empty, and non-ISO; rejection of years `0000`–`0099` (the `Date.UTC` two-digit-year trap); ordinal for epoch and known dates; civil round-trip; weekday (Mon=0 mapping); `addDays` across month/year/leap; inclusive span; lexicographic compare; month/year start; range guard. Includes 2027-12-31 → 2028-01-01 and 2028-02-29. Asserts the modules use UTC ordinals and do not shift across a non-Jakarta local zone (the suite is time-zone-invariant because no local-time constructor is used). |
| `src/lib/gantt/scale.test.ts` | `buildAxis` from project only, tasks only, union, timeline-only, draft-only, empty-ish project; 7-day minimum; single-day task; leap day; cross-year; 10-year cap and `truncated`; invalid dates counted; `barRect` inclusive width (1 day = pxPerDay, 5 days = 5×); min visual width; `clippedEnd` flag; progress uses `fullWidth` then clips to visible `width` (50 % of a right-truncated bar stays 50 % of the full span); progress clamp (negative, >100, NaN, 0, 100); today marker inside/outside. |
| `src/lib/gantt/ticks.test.ts` | Day/week/month tick steps and labels; Monday anchoring; month and year boundaries; leap day; label thinning at narrow widths; visible-window filtering (no ticks outside window); group widths and clipped edge groups; empty axis. |
| `src/lib/gantt/tasks.test.ts` | Empty list; single task; deterministic order by start date; title tie-break; id tie-break when start and title are equal; order independent of input shuffle; `filterTasks` status/PIC/title combinations (empty filters, status set, case-insensitive title, whitespace, no matches) and input immutability. |
| `src/lib/gantt/model.test.ts` | Full model: baseline/draft bars; plan marker at the end of `planAsOfDate`, at x = 0 when before the start, omitted on/after the end; task rows; `buildTaskRows` assigns `row = 0..n-1` with exactly one row per task (no two tasks ever share a row); 500 tasks produce 500 rows; `showDraft=false` removes the draft row without changing the axis; `showHolidays=false` removes holiday bands but keeps weekends and the uncovered hatch; weekend bands including Sunday-start axis; holiday bands by type; holiday on weekend; uncovered-horizon bands; painting order; today marker; no-timeline model; no-task model; truncated axis; invalid task rows excluded; zero-working-day baseline/draft retains the `NO_WORKING_DAYS` warning without changing geometry; `selectionKey` uniqueness for task/version ids. |
| `src/features/gantt/api.test.tsx` | `loadGanttTasks`: one page, three pages, stop on `last`, stop at 5-page cap, `totalElements` truncation, duplicate-id de-duplication, exact axios params (`page`, `size: 100`, `sort: 'startDate,asc'`), client sort. `loadHolidayYear`: one page, multi-page, cap, exact params (`year`, `page`, `size: 100`, `sort: 'date,asc'`). `loadHolidayCalendar`: all success, one year fails (partial), all fail, empty years. Hooks: disabled when `years=null`; empty years resolves to an empty calendar without a request; key shape; success data. |
| `src/components/gantt/GanttChart.test.tsx` | Renders baseline/draft/task bars with accessible names; no baseline when null; no fill for draft; weekend/holiday/uncovered bands present and labelled; today marker present/absent; zoom configuration changes bar widths; `showHolidays=false` removes holiday bands but keeps weekends; click calls `onSelect` with task/baseline/draft; keyboard Arrow/Home/End/Enter/Escape behaviour; focus return; text-alternative table contains every filtered task plus baseline/draft rows; hidden holiday list names each loaded holiday; empty model; clipped bar classes; horizontal scroll container present. Also renders `GanttLegend` text. |
| `src/components/gantt/GanttDetailsDrawer.test.tsx` | Task fields (title, status, PIC, dates, calendar-day caption, percent); baseline fields (version, plan/actual, plan as-of, workday caption, warning, notes); draft has no progress fields and shows "not active"; `onClose` via close button and Escape; links point to the correct project tabs. |
| `src/pages/GanttPage.test.tsx` | Loading skeleton; tasks error + retry and baseline still rendered; empty tasks + timeline; no timeline 404 note; timeline error warning with task chart; holiday partial-failure warning; horizon hatch note; task truncation notice; axis truncation notice; invalid-date notice; filter changes bars/count; zoom control changes; showDraft toggles the draft row; showHolidays toggles bands; archived project read-only text; status/PIC/search values passed to filtering; "Today" disabled when out of range. |
| `src/pages/GanttPage.integration.test.tsx` | MSW-backed composition: real `useGanttTasks` paging across three pages; timeline 404; one holiday year 500 and one 200; asserts only GET requests were observed, chart renders, warning lists the failed year. |
| `src/app/ProjectLayout.test.tsx` | Gantt tab is present and selected for `/projects/p1/gantt`; Timeline tab still selected for `/timeline`; navigation from the tab calls the route; project header still renders. |
| `src/lib/queryKeys.test.ts` **M** | Asserts `gantt.tasks('p1')` and `gantt.holidays([2025, 2026])`. Existing assertions unchanged. |

Total: 11 new test files, 1 modified test file, 3 modified source files outside the feature (`App.tsx`, `ProjectLayout.tsx`, `queryKeys.ts`), and 1 modified stylesheet.

### 7.3 Test data fixtures

- Task: 5-day task (2026-11-02..2026-11-06), single-day task, task spanning a weekend, task outside project period, task on leap day, `CLOSED` at 100, `IN_PROGRESS` at 40, `OPEN` at 0.
- Timeline: published v3 within a 3-year horizon; draft v4 extending past the baseline; draft-only aggregate; zero-working-day warning.
- Holidays: Nyepi 2026-03-19 (`NATIONAL`), joint leave 2026-03-18 (`JOINT_LEAVE`), holiday on a Saturday, holiday outside the axis.
- Horizon: 2025-01-01..2027-12-31 (seed), and a narrower horizon to exercise the hatch.

### 7.4 MSW handlers

`src/test/msw/ganttHandlers.ts` uses MSW v2 and relative wildcard paths so the handlers do not depend on the configured localhost ports:

```ts
import { http, HttpResponse } from 'msw';

export const ganttHandlers = [
  http.get('*/api/v1/projects/:projectId/tasks', ({ request }) => { … }),
  http.get('*/api/v1/projects/:projectId/timeline', () => …),
  http.get('*/api/v1/holidays', ({ request }) => {
    // honour year/page/size; set X-Holiday-Coverage-Start/End
  }),
];
```

The integration test installs `setupServer(...ganttHandlers)` in `beforeAll`, calls `server.listen({ onUnhandledRequest: 'error' })`, resets handlers between tests, and records `request.method` for every request to assert AC-16. It mocks only `@/app/ProjectLayout`'s `useProjectContext` (to supply the project); all data hooks and Axios instances are real. The timeline 404 handler returns `application/problem+json` with `code: "TIMELINE_NOT_FOUND"` so the real `getTimeline` mapping is exercised rather than mocked.

### 7.5 Coverage thresholds that must still pass

| Scope | Lines | Functions | Statements | Branches |
|---|---|---|---|---|
| Global | 95 | 95 | 95 | 85 |
| `src/lib/**/*.ts` | 100 | 100 | 100 | 90 |
| `src/auth/**/*.{ts,tsx}` | 100 | 100 | 100 | 90 |
| `src/features/**/*.ts` | 100 | 100 | 100 | 90 |
| `src/components/**/*.tsx` | 90 | 90 | 90 | 80 |

Practical notes:

- Every exported function in `src/lib/gantt` and `src/features/gantt/api.ts` must be called by a test, including small helpers; unused exports are gate failures.
- Branch coverage in `buildAxis` and `buildTicks` needs explicit cases for each guard (invalid date, empty range, cap, window edges).
- `src/pages/GanttPage.tsx` is not gated, but its behaviour is covered by page tests because it is the composition surface.
- The full coverage run is slow (~16 minutes per `docs/progress.md`); run `npx vitest run --coverage` alone.

### 7.6 Regression suites that must remain green

Baseline from `docs/progress.md`: **84 tests in 14 files**. All 14 must pass unchanged except `src/lib/queryKeys.test.ts`, which gains assertions:

1. `src/api/client.test.ts`
2. `src/auth/AuthProvider.test.tsx`
3. `src/auth/refresh.test.ts`
4. `src/auth/session.test.ts`
5. `src/components/components.test.tsx`
6. `src/features/api.test.tsx`
7. `src/lib/dates.test.ts`
8. `src/lib/errors.test.ts`
9. `src/lib/progress.test.ts`
10. `src/lib/queryKeys.test.ts` (modified)
11. `src/lib/requestId.test.ts`
12. `src/lib/validation.test.ts`
13. `src/pages/LoginPage.test.tsx`
14. `src/pages/PageFlows.test.tsx`

Specific regression risks:

- `PageFlows.test.tsx` mocks `@/app/ProjectLayout` and renders pages directly; the tab change does not affect it, but the new `GanttPage` must not be imported by any mocked module in a way that breaks the mock graph.
- `queryKeys.test.ts` asserts the exact holiday key `['holidays', 2026, 1]`; do not change the existing `holidays.list` signature. Add a separate `gantt` namespace.
- `features/api.test.tsx` asserts exact axios params for existing hooks; do not change `listTasks`, `getTimeline`, or `useHolidays` behaviour.
- `components/components.test.tsx` iterates every `StatusTag` value; Gantt reuses `StatusTag` without new values.

### 7.7 Manual verification checklist

1. Chrome and Edge at 1366×768 and 1024×768: sticky header/column, toolbar wrapping, drawer width.
2. Keyboard-only: Tab into chart, arrow between bars, Enter/Space opens, Escape closes and returns focus.
3. Network panel: only GET requests from `/gantt`; no writes.
4. Zoom day/week/month: tick labels legible, bars aligned, today marker aligned.
5. Holiday degradation: block `GET /api/v1/holidays` in DevTools (Network → request blocking) while leaving the rest of timeline-service reachable, because holidays are served by timeline-service; weekend shading must remain and a holiday warning must name the failed years. Separately stopping the whole timeline-service also removes the baseline, which must produce the warning described in §3.7 and must not blank the chart.
6. Project archived: chart read-only, no controls that write.

---

## 8. Risks and mitigations

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | Very long project spans (project `startDate`/`endDate` have no maximum) | Axis of decades, unusable zoom, many ticks | Axis capped at 3,653 days, tick windowing, truncation notice. Sign-off decision S2. |
| R2 | Hundreds of tasks | Slow render, tall chart, paging cost | Cap 500 tasks/5 pages, one row per task (≈15,000 px scroll height), labels only in sticky column, no per-row fetch. Truncation notice. |
| R3 | Task paging instability (no unique sort key in the API) | A task could be skipped or duplicated across pages | Sequential paging, de-duplicate by id, client re-sort, truncation notice. Backend tie-breaker explicitly out of scope; documented limitation. |
| R4 | Holiday horizon only 2025-01-01..2027-12-31 | Days outside look like working days if not marked | Hatch uncovered ranges using the `X-Holiday-Coverage-*` headers; legend explains "no holiday data". Never fabricate holidays. |
| R5 | Date-only timezone off-by-one | Bars shifted by a day for some users | UTC ordinals, validated parsing, Jakarta today, display/arithmetic separation, dedicated tests (AC-17). Code review rejects `new Date('YYYY-MM-DD')` in `src/lib/gantt`. |
| R6 | `percentPlan` misread as calendar progress | Misleading bar fill | Plan drawn as a marker at `planAsOfDate`, number in details; no plan-width fill. |
| R7 | Draft shown as "0 % actual" | Misleading "no progress" signal | Draft row uses no progress fill and says "not active"; details omit operational values. |
| R8 | Weekend band count at day zoom | DOM bloat | One band per weekend (~156/3 years), not per day; shared background layer. |
| R9 | Sticky column overlaps bars at scroll edges | Visual artifact / click interception | Opaque label background, explicit z-index order (corner > header > labels > today marker > bars > bands), pointer-events none on bands and today marker. |
| R10 | Holiday/shading request failure blocks page | Unusable chart | Independent queries; holidays and timeline are non-blocking; only tasks gate the skeleton. |
| R11 | Coverage regression from new lib code | Build fails | Pure functions with table tests; no unused exports; test each guard branch; run coverage locally before handoff. |
| R12 | "Today" stale after midnight | Marker on the wrong day for a left-open tab | Recomputed on render; documented. A future timer would be a small additive change; not required for v1. |
| R13 | Filtering renumbers rows, so a task jumps vertically | Minor UX confusion | Axis stays fixed; row order is always `(startDate, title, id)`, so relative order is stable and only the absolute row number changes; documented in help text. |
| R14 | Large holiday year response (>100 rows) | Incomplete shading | Loader pages up to 5×100 per year; seed is well below 100 (`docs/progress.md`: 78 entries for 2025–2027). |
| R15 | Ant Design table/drawer bundle growth | Slower initial load | No new library; route-level code splitting already applies to pages; components reuse existing AntD primitives. |

---

## 9. Traceability

| AC | Design section | Components / functions | Tests |
|---|---|---|---|
| AC-1 | §2.1 | `App.tsx`, `ProjectLayout.tsx`, `GanttPage` | `src/app/ProjectLayout.test.tsx`; `GanttPage.test.tsx` |
| AC-2 | §4.2, §4.5 | `buildAxis`, `barRect`, `GanttChart` | `scale.test.ts`, `GanttChart.test.tsx` |
| AC-3 | §2.6, §3.3, §4.5 | `useTimeline`, `buildChartModel`, baseline row | `model.test.ts`, `GanttPage.test.tsx`, `GanttChart.test.tsx` |
| AC-4 | §2.6, §3.3, §4.5 | draft row, `GanttDetailsDrawer` | `model.test.ts`, `GanttChart.test.tsx`, `GanttDetailsDrawer.test.tsx` |
| AC-5 | §3.4, §4.6 | `loadHolidayYear`, `buildWeekendBands`, `buildHolidayBands`, `GanttLegend` | `api.test.tsx`, `model.test.ts`, `GanttChart.test.tsx` |
| AC-6 | §4.7 | `jakartaToday`, `todayX` | `scale.test.ts`, `GanttChart.test.tsx` |
| AC-7 | §2.3, §4.8 | `ZOOM_CONFIG`, `buildTicks`, `GanttPage` | `ticks.test.ts`, `scale.test.ts`, `GanttPage.test.tsx` |
| AC-8 | §2.4 | `GanttChart` sticky CSS | `GanttChart.test.tsx` |
| AC-9 | §2.7 | `GanttDetailsDrawer`, `onSelect` | `GanttChart.test.tsx`, `GanttDetailsDrawer.test.tsx` |
| AC-10 | §2.8 | roving tabindex, aria labels, text table | `GanttChart.test.tsx` |
| AC-11 | §2.2, §4.4 | `filterTasks`, `GanttPage` toolbar | `tasks.test.ts`, `GanttPage.test.tsx` |
| AC-12 | §3.2 | `loadGanttTasks` | `api.test.tsx`, `GanttPage.integration.test.tsx` |
| AC-13 | §3.7 | `useTimeline`, `GanttPage` notices | `GanttPage.test.tsx`, `GanttPage.integration.test.tsx` |
| AC-14 | §3.4, §3.7 | `loadHolidayCalendar` | `api.test.tsx`, `GanttPage.test.tsx`, integration |
| AC-15 | §2.6 | `GanttPage`, shared archived banner | `GanttPage.test.tsx` |
| AC-16 | §0.1, §3.2, §3.4 | query layer only uses GET | integration test method log; manual network check |
| AC-17 | §4.1, §4.10 | `dateMath.ts` | `dateMath.test.ts`, `scale.test.ts`, `ticks.test.ts` |
| AC-18 | §5.4, §7.5, §7.6 | coverage config unchanged | full `npx vitest run --coverage`; regression suites |

---

## 10. Decisions requiring user sign-off

These are the only decisions that change observable product behaviour or scope. Everything else follows the approved design and the constraints above.

| # | Decision | Recommendation | Alternative |
|---|---|---|---|
| S1 | **Rendering approach**: custom DOM/CSS chart, no Gantt library. | Recommended (§6) for fidelity, accessibility, licence, and zero dependency growth. | Adopt a library (extra licence/bundle/disabled interaction). |
| S2 | **Task cap and axis cap**: 500 tasks and 3,653 days, with truncation notices. | Recommended: covers the release-1 profile (≤50,000 tasks across ≤100 projects, 3-year horizon) and bounds render/request cost. | Raise to 1,000 tasks / keep 10-year axis, at higher paging and DOM cost; or lower to 200 for stricter performance. |
| S3 | **Axis domain**: union of project period, loaded task extents, and timeline/draft extents. | Recommended: never hides real work outside the project period. | Use the project period only and clip tasks outside it (simpler but can hide work). |
| S4 | **Row layout**: one row per task, ordered by `(startDate, title, id)`, rather than interval-packed lanes. | Recommended: the sticky label column shows exactly one name per row, so every task is identifiable without clicking; height is bounded by the 500-task cap. | Interval packing: shorter chart for sequential work, but tasks sharing a lane (all but the first) lose their visible label and would require in-bar labels. |
| S5 | **Plan representation**: `percentPlan` as a marker at `planAsOfDate`, not a fill width. | Recommended: honest, because working-day progress is not linear in calendar time. | Fill the baseline to `percentPlan` (conventional but implies a date that is not meaningful). |

If S1–S5 are accepted as recommended, implementation can start without further clarification.

---

## 11. Implementation plan

### 11.1 Ordered steps

1. **Pure date math** — add `src/lib/gantt/dateMath.ts` and `dateMath.test.ts`. No React imports. Confirm ordinal math across year/leap boundaries.
2. **Scale and bars** — add `src/lib/gantt/scale.ts` and tests: `ZOOM_CONFIG`, `buildAxis`, `barRect`, clipping, caps, today marker.
3. **Ticks** — add `src/lib/gantt/ticks.ts` and tests: visible-window generation, Monday anchoring, thinning, groups.
4. **Ordering and filters** — add `src/lib/gantt/tasks.ts` and tests: deterministic ordering, tie-breaks, filter combinations, input immutability.
5. **Chart model** — add `src/lib/gantt/model.ts` and tests: compose axis/task rows/bars/bands, one-row-per-task invariant, painting order, uncovered horizon, selection keys.
6. **Query keys and composition** — modify `src/lib/queryKeys.ts` (+ test); add `src/features/gantt/api.ts` and `api.test.tsx`: sequential task paging, holiday year paging, partial failure, exact params.
7. **Presentational components** — add `src/components/gantt/GanttChart.tsx`, `GanttLegend.tsx`, `GanttDetailsDrawer.tsx`; add `src/styles.css` classes; add component tests (roles, names, keyboard, selection).
8. **Page** — add `src/pages/GanttPage.tsx`: filters, zoom, toggles, notices, drawer, model memo; add `GanttPage.test.tsx`.
9. **Routing and tab** — modify `src/app/App.tsx` and `src/app/ProjectLayout.tsx`; add `ProjectLayout.test.tsx`.
10. **MSW integration** — add `src/test/msw/ganttHandlers.ts` and `GanttPage.integration.test.tsx`; assert paging, timeline 404, holiday partial failure, GET-only.
11. **Gate run and cleanup** — run all four commands (§11.3), fix coverage/type/lint findings, verify the manual checklist (§7.7).
12. **Handoff** — provide a short implementation note listing deviations (if any) for the design reviewer; do not modify `docs/design.md`.

### 11.2 Files added/modified

| Action | File |
|---|---|
| Add | `src/lib/gantt/dateMath.ts`, `scale.ts`, `ticks.ts`, `tasks.ts`, `model.ts` |
| Add | `src/features/gantt/api.ts` |
| Add | `src/components/gantt/GanttChart.tsx`, `GanttLegend.tsx`, `GanttDetailsDrawer.tsx` |
| Add | `src/pages/GanttPage.tsx` |
| Add | `src/test/msw/ganttHandlers.ts` |
| Add | Tests: `dateMath.test.ts`, `scale.test.ts`, `ticks.test.ts`, `tasks.test.ts`, `model.test.ts`, `api.test.tsx`, `GanttChart.test.tsx`, `GanttDetailsDrawer.test.tsx`, `GanttPage.test.tsx`, `GanttPage.integration.test.tsx`, `ProjectLayout.test.tsx` |
| Modify | `src/app/App.tsx` (one route) |
| Modify | `src/app/ProjectLayout.tsx` (one tab + active-key branch) |
| Modify | `src/lib/queryKeys.ts` (one `gantt` namespace) |
| Modify | `src/lib/queryKeys.test.ts` (two assertions) |
| Modify | `src/styles.css` (Gantt classes) |
| Unchanged | `docs/design.md`, all backend modules, deploy/Compose, Nginx, auth, gateway configuration, `TimelinePage.tsx`, `TasksPage.tsx` |

No backend file, migration, or contract is touched.

### 11.3 Definition of done

All commands run from `coding/frontend`:

```bash
npx tsc -p tsconfig.json --noEmit
npm run lint
npx vitest run --coverage
npm run build
```

DoD checklist:

1. `npx tsc -p tsconfig.json --noEmit` exits 0 with no errors.
2. `npm run lint` exits 0 with no errors. Existing fast-refresh warnings must not increase; new components export only components (types are type-only).
3. `npx vitest run --coverage` passes all pre-existing 84 tests in 14 files plus the new Gantt tests, and meets every threshold in §7.5 (global, lib, auth, features, components).
4. `npm run build` succeeds (it also re-runs `tsc`), producing `dist/`.
5. Route `/projects/:projectId/gantt` renders in a browser with a seeded project; the Timeline tab is unchanged.
6. Network inspection shows only GET requests from the Gantt route.
7. Manual accessibility check passes: keyboard traversal, visible focus, drawer focus return, text-alternative table.
8. No change to `docs/design.md`, no backend diff, no new dependency in `package.json`.
9. The design reviewer can map every AC in §1.4 to the traceability table in §9 and to a passing test.

---

## Appendix A — Worked geometry example

Input:

- Project: 2026-11-02 .. 2027-04-15.
- Tasks: T1 2026-11-02..2026-11-06 (5 days), T2 2026-11-04..2026-11-10 (7 days), T3 2026-11-09..2026-11-09 (1 day).
- Current published: v3 2026-11-02..2027-04-15.
- Draft: v4 2026-11-02..2027-04-22.
- Zoom: week, `pxPerDay = 8`.

Ordinals (relative, for illustration): let `d(2026-11-02) = 0`.

- Axis: start = min = 0; end = max(draft end = 2027-04-22) = 171 days later; `dayCount = 172`; no truncation.
- Total track width = 172 × 8 = 1,376 px.
- Baseline: x = 0, width = 165 × 8 = 1,320 px (2026-11-02..2027-04-15 inclusive = 165 days).
- Draft: x = 0, width = 172 × 8 = 1,376 px.
- T1: x = 0, width = 5 × 8 = 40 px.
- T2: x = 2 × 8 = 16 px, width = 7 × 8 = 56 px.
- T3: x = 7 × 8 = 56 px, width = 1 × 8 = 8 px.
- Rows (sorted T1, T2, T3 by start date): T1 → row 0; T2 → row 1; T3 → row 2. Each task has exactly one row, so the sticky label column shows all three names and no bar can collide with another.
- T1 progress 40 % → fill 16 px. T2 100 % → 56 px. T3 0 % → 0 px.
- Baseline plan marker for a fixture whose `planAsOfDate = 2026-12-15` and `percentPlan = 30.19` (the v1.1 sample): 2026-11-02..2026-12-15 inclusive is 44 days, so the marker is at `44 × 8 = 352 px` from the baseline start. A naive 30.19 % fill would end at `1,320 × 0.3019 ≈ 398.5 px`, a 46 px (≈6 day) difference. This is the concrete reason §4.5 draws a marker rather than a plan-width fill. Live current versions report Jakarta today as the as-of date (§3.3), so the live marker normally sits one cell right of the today line; the fixture keeps the arithmetic independent of the run date.
- If a holiday falls on 2026-11-05, one band at `x = 3 × 8 = 24`, width = 8 px, painted above the weekday background.
- Weekend bands: Saturdays 2026-11-07 (x = 40, width 16), 2026-11-14 (x = 96, width 16), …

This example is encoded in `scale.test.ts` / `model.test.ts` as a regression fixture.

---

## Appendix B — Request/response assumptions summary

| Assumption | Verified in code |
|---|---|
| Task page size maximum 100, default 20 | `common/api/PageQuery.java` (`MAX_SIZE = 100`, `DEFAULT_SIZE = 20`) |
| Task sort allow-list includes `startDate`, not `id` | `task-service/.../TaskService.java` `SORT_FIELDS` |
| Task list returns `PageResponse<TaskSummary>` with `id`, `title`, `startDate`, `endDate`, `durationDays`, `pic`, `status`, `percentComplete` | `types/api.ts`; `TaskController.list` |
| Timeline aggregate returns `current` and `draft`, 404 `TIMELINE_NOT_FOUND` when absent | `TimelineController.aggregate`; `getTimeline` in `features/timeline/api.ts` |
| Holiday list defaults to size 100 and accepts `year` | `HolidayController.list` |
| Holiday sort accepts `date` (rewritten to `holidayDate`) | `HolidayService.list` |
| Horizon headers are exposed and read by the frontend | `HolidayController`; `useHolidayHorizon` |
| Frontend query defaults: stale 20 s, retry once for 5xx, no 4xx retry | `main.tsx` |
| Coverage include/exclude and thresholds | `vite.config.ts` |
| Baseline suite: 84 tests / 14 files | `docs/progress.md` §4 |

If any of these code facts changes before implementation, this addendum must be re-reviewed before coding.
