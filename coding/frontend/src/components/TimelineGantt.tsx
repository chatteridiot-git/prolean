import { useRef, useState } from 'react';
import type { KeyboardEvent as ReactKeyboardEvent } from 'react';
import { Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { StatusTag } from '@/components/StatusTag';
import { daysBetweenInclusive, formatDate } from '@/lib/dates';
import { formatPercent } from '@/lib/progress';
import { statusLabel } from '@/lib/status';
import type { LifecycleStatus, OperationalStatus, TimelineVersion } from '@/types/api';

const MAX_VERSIONS = 10;
const DAY_MS = 86_400_000;

type TimelineGanttRow = {
  id: string;
  versionNumber: number;
  lifecycleStatus: LifecycleStatus;
  operationalStatus: OperationalStatus;
  startDate: string;
  endDate: string;
  durationWorkdays: number;
  percentPlan: number;
  percentActual: number;
  isCurrent: boolean;
};

export type TimelineGanttProps = {
  versions: TimelineVersion[];
  currentVersionId?: string;
};

/**
 * Selects at most MAX_VERSIONS versions by priority:
 * current published, active draft, latest superseded, latest cancelled, then
 * the remaining versions in descending version order. Values such as
 * percentPlan/percentActual are carried through untouched; the chart never
 * recomputes API-derived data.
 */
function buildGanttRows(
  versions: TimelineVersion[],
  currentVersionId?: string,
): TimelineGanttRow[] {
  const pool: TimelineVersion[] = [];
  const seen = new Set<string>();
  for (const version of versions) {
    if (!seen.has(version.id)) {
      seen.add(version.id);
      pool.push(version);
    }
  }
  // "Latest" in the priority order means highest versionNumber; sort defensively
  // so correctness does not depend on the caller's input order.
  pool.sort((a, b) => b.versionNumber - a.versionNumber);

  const picks: TimelineVersion[] = [];
  const taken = new Set<string>();
  const takeFirst = (matches: (version: TimelineVersion) => boolean) => {
    const candidate = pool.find((version) => !taken.has(version.id) && matches(version));
    if (candidate) {
      taken.add(candidate.id);
      picks.push(candidate);
    }
  };

  takeFirst((version) =>
    currentVersionId
      ? version.id === currentVersionId
      : version.lifecycleStatus === 'PUBLISHED',
  );
  takeFirst((version) => version.lifecycleStatus === 'DRAFT');
  takeFirst((version) => version.lifecycleStatus === 'SUPERSEDED');
  takeFirst((version) => version.lifecycleStatus === 'CANCELLED');

  for (const version of pool) {
    if (picks.length >= MAX_VERSIONS) break;
    if (taken.has(version.id)) continue;
    taken.add(version.id);
    picks.push(version);
  }

  return picks
    .slice()
    .sort((a, b) => a.versionNumber - b.versionNumber)
    .map((version) => ({
      id: version.id,
      versionNumber: version.versionNumber,
      lifecycleStatus: version.lifecycleStatus,
      operationalStatus: version.operationalStatus,
      startDate: version.startDate,
      endDate: version.endDate,
      durationWorkdays: version.durationWorkdays,
      percentPlan: version.percentPlan,
      percentActual: version.percentActual,
      isCurrent: Boolean(currentVersionId && version.id === currentVersionId),
    }));
}

/**
 * Read-only Gantt of timeline versions (never tasks). Bars are positioned from
 * calendar-day spans over the inclusive date domain of the selected versions.
 */
export function TimelineGantt({ versions, currentVersionId }: TimelineGanttProps) {
  const rows = buildGanttRows(versions, currentVersionId);
  const barRefs = useRef<Array<HTMLDivElement | null>>([]);
  // -1 means focus is on the container itself, not on any bar row yet.
  const [activeIndex, setActiveIndex] = useState(-1);

  if (rows.length === 0) {
    return (
      <div className="timeline-gantt" role="group" aria-label="Timeline version gantt">
        <Typography.Paragraph type="secondary" className="timeline-gantt-empty">
          No versions yet.
        </Typography.Paragraph>
      </div>
    );
  }

  const startTimes = rows.map((row) => dayjs(row.startDate).valueOf());
  const endTimes = rows.map((row) => dayjs(row.endDate).valueOf());
  const domainStart = Math.min(...startTimes);
  const totalDays = Math.round((Math.max(...endTimes) - domainStart) / DAY_MS) + 1;

  /**
   * Roving focus: the group is the only tab stop; arrow keys move focus across
   * row bars so keyboard and screen-reader users can read every version.
   */
  const moveFocus = (nextIndex: number) => {
    const target = Math.min(Math.max(nextIndex, 0), rows.length - 1);
    setActiveIndex(target);
    barRefs.current[target]?.focus();
  };

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    // The container only renders when rows exist, so no empty-row guard is needed.
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      moveFocus(activeIndex === -1 ? 0 : activeIndex + 1);
    } else if (event.key === 'ArrowUp') {
      if (activeIndex === -1) return;
      event.preventDefault();
      moveFocus(activeIndex - 1);
    } else if (event.key === 'Home') {
      event.preventDefault();
      moveFocus(0);
    } else if (event.key === 'End') {
      event.preventDefault();
      moveFocus(rows.length - 1);
    }
  };

  return (
    <div
      className="timeline-gantt"
      role="group"
      aria-label="Timeline version gantt"
      tabIndex={0}
      onKeyDown={handleKeyDown}
    >
      <div className="timeline-gantt-grid">
        {rows.map((row, index) => {
          const leftDays = Math.round(
            (dayjs(row.startDate).valueOf() - domainStart) / DAY_MS,
          );
          const spanDays = daysBetweenInclusive(row.startDate, row.endDate);
          const ariaLabel = [
            `v${row.versionNumber}`,
            statusLabel(row.lifecycleStatus),
            statusLabel(row.operationalStatus),
            `${formatDate(row.startDate)} – ${formatDate(row.endDate)}`,
            `${row.durationWorkdays} workdays`,
            `plan ${formatPercent(row.percentPlan)}`,
            `actual ${formatPercent(row.percentActual)}`,
          ].join(', ');
          return (
            <div className="timeline-gantt-row" key={row.id}>
              <div className="timeline-gantt-label">
                <div className="timeline-gantt-label-title">
                  <Tag color="geekblue">v{row.versionNumber}</Tag>
                  <StatusTag value={row.lifecycleStatus} />
                  <StatusTag value={row.operationalStatus} />
                </div>
                <div className="timeline-gantt-label-meta small-text">
                  {formatDate(row.startDate)} – {formatDate(row.endDate)} ·{' '}
                  {row.durationWorkdays} workdays
                </div>
                <div className="timeline-gantt-label-meta small-text">
                  Plan {formatPercent(row.percentPlan)} · Actual{' '}
                  {formatPercent(row.percentActual)}
                </div>
              </div>
              <div className="timeline-gantt-track">
                <div
                  ref={(element) => {
                    barRefs.current[index] = element;
                    return () => {
                      barRefs.current[index] = null;
                    };
                  }}
                  className={`timeline-gantt-bar timeline-gantt-bar-${row.lifecycleStatus.toLowerCase()}${
                    row.isCurrent ? ' timeline-gantt-bar-current' : ''
                  }`}
                  style={{
                    left: `${(leftDays / totalDays) * 100}%`,
                    width: `${(spanDays / totalDays) * 100}%`,
                  }}
                  role="img"
                  aria-label={ariaLabel}
                  tabIndex={-1}
                >
                  <span className="timeline-gantt-bar-text">v{row.versionNumber}</span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
