# ProLean — Timeline Gantt Chart Design

**Status:** Approved — implemented (implementation revision appended below)
**Date:** 22 October 2026
**Requested feature:** Gantt chart applied for Timeline

---

## 1. Scope and intent

The Gantt chart is a **version-level** visual of one project's timeline history. It is not an
activity-level chart. It shows no task bars, dependencies, resource levelling, or editing. It is
an approved design revision of the timeline feature, scoped strictly to timeline versions.

- One Gantt per project on the Timeline page.
- One row per timeline version.
- No backend endpoint changes.
- No task-level data.

---

## 2. Functional behavior

### 2.1 Displayed versions

- The chart consumes a **dedicated page-0 slice** of timeline versions from the existing
  paginated API, independent of the version-history table's current page.
- At most 10 versions are shown.
- Priority order is:
  1. current published version,
  2. active draft,
  3. latest superseded version,
  4. latest cancelled version,
  5. remaining versions by descending `versionNumber`.
- If a version is cancelled, its bar uses the cancelled style.

### 2.2 Labels

Each row shows:

- `v{n}`
- lifecycle status as text (Reused / Draft / Superseded / Cancelled via the existing StatusTag
  palette)
- operational status as text (`OPEN` / `IN_PROGRESS` / `CLOSED`)
- start–end dates
- working-day duration
- percentPlan
- percentActual

Status is never indicated by colour alone. The existing text labels are always visible.

### 2.3 Tooltip / description

A native browser tooltip is **not used** for accessibility. Hover/focus uses an accessible label
with `aria-label` or `aria-describedby`. Screen readers can therefore read version, dates,
statuses, and progress without relying on a `title` attribute.

### 2.4 Geometry and geometry rule

- Bars use **calendar-day widths** because the underlying timeline dates are calendar-day ranges.
- `durationWorkdays` is shown in the label/tooltip, and is intentionally not used to set bar width.
- The active domain is the inclusive span:
  - domain start = minimum `startDate` across displayed rows
  - domain end = maximum `endDate` across displayed rows
- Each date is inclusive; `startDate === endDate` renders a minimum-width bar, not zero width.
- Bars never overlap across rows.

### 2.5 Minimum width and reflow

- At tablet width, the chart is rendered as an **internally scrollable region** with a sticky
  label column, not as page-level horizontal scroll.
- Bars have a **minimum visible width** of 24 px.
- At laptop width the chart uses full available width without overflow.

---

## 3. Component placement and data flow

### 3.1 Component

- `src/components/TimelineGantt.tsx`
- `src/components/TimelineGantt.test.tsx`
- `src/pages/TimelinePage.tsx` integration point.

**Placement revision (implementation):** the original plan placed the component in
`features/timeline/`, but the coverage gate measures `src/features/**/*.ts` only — TSX files
under `features/` fall entirely outside the gate. The component therefore exercises the
fallback recorded in this section and lives in `src/components/`, where the
`src/components/**/*.tsx` gate (90% thresholds) applies, matching `PercentBar`/`StatusTag`
conventions. The status vocabulary was extracted to `src/lib/status.ts` so the Gantt and
`StatusTag` share one source for labels and colors.

### 3.2 Props

```ts
type TimelineGanttProps = {
  versions: TimelineVersion[];
  currentVersionId?: string;
};
```

- `versions` is a **dedicated 10-version page-0 slice**, independent of table paging.
- `currentVersionId` is the aggregate's current version id when one exists.
- No mutation or edit capability.

### 3.3 Existing data source

Reuses the existing `useTimelineVersions(projectId, 0)` hook from
`features/timeline/api.ts`. No new backend endpoint or query key is introduced.

### 3.4 Type mapping

```ts
type TimelineGanttRow = {
  id: string;
  versionNumber: number;
  lifecycleStatus: LifecycleStatus;
  operationalStatus: OperationalStatus;
  startDate: string;
  endDate: string;
  durationWorkdays: number;
  percentActual: number;
  percentPlan: number;
};
```

This uses the existing `TimelineVersion` shape. Values such as `percentPlan` and
`percentActual` are **not recomputed**; they are shown exactly as returned by the API.

---

## 4. Visual design

### 4.1 Palette

Reuse existing `StatusTag` colors to avoid introducing a second, colliding color language:

| LifecycleStatus | Color |
|---|---|
| `PUBLISHED` | green |
| `DRAFT` | orange |
| `SUPERSEDED` | neutral grey |
| `CANCELLED` | red |

Operational status is **not** represented by colour. It is always shown as text using the same
palette/vocabulary as the existing status chips (`StatusTag`) so the chart and version table
remain consistent.

### 4.2 Row layout

- One row per timeline version.
- Row height 28 px.
- Bars are drawn as horizontal bands.
- The current published version is emphasized with higher contrast and a stronger border.
- The draft bar is visually distinct from prior versions.

### 4.3 Accessibility

- The Gantt container is `role="group"` with a descriptive label.
- Keyboard support uses a single tab stop with arrow-key navigation between rows, plus visible
  focus.
- Screen-reader text is provided for each bar.
- Colour contrast meets WCAG 2.1 AA.

---

## 5. Tests

### 5.1 Unit tests

- Rows map 1:1 from exactly 10 timeline versions.
- Values (`percentPlan`, `percentActual`) are not recalculated.
- Ordering and priority selection follow the rule in §2.1.
- Bars use inclusive date width with a minimum visible width.

### 5.2 Component tests

- Renders 1 current published bar and 1 draft bar.
- Renders lifecycle colour classes consistent with the existing StatusTag palette.
- Provides accessible labels that do not rely on colour or `title`.
- Handles empty timeline state.

### 5.3 Page-flow tests

- Timeline page renders the Gantt when versions exist.
- Timeline page continues to render the history table and existing conflict flows.
- The Gantt remains stable when the history table pages.

### 5.4 Responsive tests

- Laptop ≥1200 px width.
- Tablet 768–1199 px width.

---

## 6. Acceptance criteria

- No new backend endpoint and no new third-party charting library.
- Chart is read-only and displays only timeline versions.
- Colors match existing status vocabulary, with no colour-only semantic information.
- Keyboard and screen-reader accessible.
- No page-level horizontal scroll on tablet.
- Percent values come directly from the API and are not recomputed.
- Both tablet and laptop widths are covered by tests.

---

## 7. Implementation notes (post-approval revision, 22 October 2026)

### 7.1 Files touched

- Added `src/components/TimelineGantt.tsx` — presentational, read-only, no network calls.
- Added `src/components/TimelineGantt.test.tsx` — 8 component tests.
- Added `src/lib/status.ts` — shared status label/color vocabulary (`statusLabel`, `statusColor`)
  plus 100% coverage via existing component tests.
- Updated `src/components/StatusTag.tsx` to consume `src/lib/status.ts` (no behavior change).
- Updated `src/pages/TimelinePage.tsx` — Gantt card between the summary cards and the version
  history card, fed by a dedicated `useTimelineVersions(projectId, 0)` query.
- Updated `src/styles.css` — gantt row/track/bar styles, sticky label column, internal scroll,
  24 px minimum bar width, focus outlines.
- Updated `src/pages/PageFlows.test.tsx` — Timeline page now asserts the Gantt renders and that
  existing flow controls remain intact.

### 7.2 Concrete behaviors that refine the approved design

- Bars are positioned with `left`/`width` percentages computed from **calendar-day spans**
  over the inclusive domain (`min(startDate)` → `max(endDate)` of displayed rows). A
  `startDate === endDate` version spans 1 day; the CSS `min-width: 24px` guarantees visibility.
- The row label column is `position: sticky` inside the internally scrolled region
  (grid min-width 720px) so tablet scrolling never scrolls labels away.
- Each bar carries `role="img"` + `aria-label` listing version, lifecycle, operational status,
  dates, workdays, plan and actual — no native `title` tooltip is used.
- Keyboard: the group is the only tab stop. `ArrowDown` enters the first row; `ArrowUp`/`ArrowDown`
  move between rows and clamp at the ends; `Home`/`End` jump to the first/last row; other keys
  are ignored so they can bubble to the page.
- Bar colors use darker antd palette shades (`#237804`, `#873800`, `#595959`, `#a8071a`) for
  white-text contrast; the current published version gets a stronger border ring. Status text is
  always present in the row label via `StatusTag`, so colour is never the only signal.
- Support note "Up to 10 versions" is shown in the card header (rendered unconditionally).

### 7.3 Responsive validation (reconciles §5.4 and §6)

The tablet/laptop responsiveness is delivered as a CSS contract: `styles.css` keeps the chart
inside its own overflow region (grid `min-width: 720px`, sticky 240px label column), so no
page-level horizontal scroll appears at tablet widths. jsdom cannot compute layout or viewport
widths, so automated viewport tests are not possible in this repo (same reasoning as the
placement fallback in §3.1); the transition points (`max-width: 1024px` / `640px` blocks) follow
the breakpoints already documented for the rest of the application. Manual check points at
768px and ≥1200px widths are part of acceptance.

### 7.4 Code-review outcomes (22 October 2026)

Review verdict: APPROVED with non-blocking findings. Applied:

- Gantt card now mirrors the version-history card's loading (skeleton) and error (Alert) states
  instead of showing "No versions yet." during first load or failure (TimelinePage integration).
- `buildGanttRows` sorts the pool by descending `versionNumber` so the "latest superseded /
  cancelled" priority does not depend on caller input order.
- Unreachable empty-row guard removed from the keyboard handler; bar `ref` callbacks return
  explicit React 19 cleanup; the page-flow test pin asserts the dedicated
  `useTimelineVersions(projectId, 0)` call.

Deferred (non-blocking nits, recorded for a later pass): optional reset of the roving focus
index on refetch-driven row changes; timezone-policy alignment of date math with the
Jakarta helpers; "1 workdays" label grammar; degenerate-data guards (backend already
validates ranges and dates).
