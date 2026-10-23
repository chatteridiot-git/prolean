import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { TimelineGantt } from '@/components/TimelineGantt';
import type { TimelineVersion } from '@/types/api';

const base: TimelineVersion = {
  id: 'v1',
  timelineId: 'tl1',
  projectId: 'p1',
  versionNumber: 1,
  lifecycleStatus: 'PUBLISHED',
  startDate: '2026-11-02',
  endDate: '2026-11-06',
  durationWorkdays: 5,
  operationalStatus: 'OPEN',
  percentActual: 10,
  percentPlan: 20,
  planAsOfDate: '2026-11-02',
  createdBy: 'u1',
  createdAt: '2026-11-02T00:00:00Z',
  updatedAt: '2026-11-02T00:00:00Z',
};

function version(
  overrides: Partial<TimelineVersion> & { versionNumber: number },
): TimelineVersion {
  return {
    ...base,
    id: `v${overrides.versionNumber}`,
    ...overrides,
  };
}

function ganttGroup() {
  return screen.getByRole('group', { name: 'Timeline version gantt' });
}

function percentOf(bar: Element, property: 'left' | 'width'): number {
  const value = (bar as HTMLElement).style[property] ?? '0';
  return Number(value.replace('%', ''));
}

describe('TimelineGantt', () => {
  it('renders one row per unique version in ascending version order', () => {
    const published = version({ versionNumber: 1 });
    const draft = version({ versionNumber: 2, lifecycleStatus: 'DRAFT' });
    render(<TimelineGantt versions={[draft, { ...draft }, published]} />);

    const group = ganttGroup();
    const bars = within(group).getAllByRole('img');
    expect(bars).toHaveLength(2);
    expect(bars[0]).toHaveClass('timeline-gantt-bar-published');
    expect(bars[1]).toHaveClass('timeline-gantt-bar-draft');
    expect(bars[0]).not.toHaveClass('timeline-gantt-bar-current');
    // API values pass through unchanged, never recomputed.
    expect(
      within(group).getAllByText('Plan 20.00% · Actual 10.00%'),
    ).toHaveLength(2);
  });

  it('describes every bar for screen readers without a native tooltip', () => {
    render(<TimelineGantt versions={[version({ versionNumber: 1 })]} />);

    const bar = within(ganttGroup()).getByRole('img', {
      name: 'v1, Published, Open, 02 Nov 2026 – 06 Nov 2026, 5 workdays, plan 20.00%, actual 10.00%',
    });
    expect(bar).toBeInTheDocument();
    expect(bar).not.toHaveAttribute('title');
  });

  it('prioritizes current, draft, superseded and cancelled versions', () => {
    const published = version({ versionNumber: 2 });
    const superseded = version({ versionNumber: 3, lifecycleStatus: 'SUPERSEDED' });
    const draft = version({ versionNumber: 4, lifecycleStatus: 'DRAFT' });
    const cancelled = version({ versionNumber: 5, lifecycleStatus: 'CANCELLED' });
    render(
      <TimelineGantt
        versions={[cancelled, draft, superseded, published]}
        currentVersionId={published.id}
      />,
    );

    const group = ganttGroup();
    const bars = within(group).getAllByRole('img');
    expect(bars.map((bar) => bar.textContent)).toEqual(['v2', 'v3', 'v4', 'v5']);
    expect(bars[0]).toHaveClass('timeline-gantt-bar-current');
    expect(bars[1]).toHaveClass('timeline-gantt-bar-superseded');
    expect(bars[2]).toHaveClass('timeline-gantt-bar-draft');
    expect(bars[3]).toHaveClass('timeline-gantt-bar-cancelled');
    expect(within(group).queryByText('v1')).not.toBeInTheDocument();
  });

  it('caps the chart at ten versions by descending version priority', () => {
    const versions: TimelineVersion[] = [];
    for (let number = 12; number >= 1; number -= 1) {
      versions.push(
        version({
          versionNumber: number,
          lifecycleStatus:
            number === 12 ? 'PUBLISHED' : number === 11 ? 'DRAFT' : 'SUPERSEDED',
        }),
      );
    }
    render(<TimelineGantt versions={versions} currentVersionId="v12" />);

    const group = ganttGroup();
    const bars = within(group).getAllByRole('img');
    expect(bars).toHaveLength(10);
    expect(bars.at(-1)).toHaveClass('timeline-gantt-bar-current');
    expect(within(group).queryByText('v1')).not.toBeInTheDocument();
    expect(within(group).queryByText('v2')).not.toBeInTheDocument();
  });

  it('positions bars from calendar-day spans of the inclusive date domain', () => {
    const early = version({ versionNumber: 1 });
    const later = version({
      versionNumber: 2,
      lifecycleStatus: 'DRAFT',
      startDate: '2026-12-01',
      endDate: '2026-12-05',
    });
    render(<TimelineGantt versions={[later, early]} />);

    const group = ganttGroup();
    const bars = within(group).getAllByRole('img');
    // Domain: 02 Nov – 05 Dec = 34 inclusive days.
    expect(Math.abs(percentOf(bars[0], 'left'))).toBeLessThan(0.001);
    expect(percentOf(bars[0], 'width')).toBeCloseTo((5 / 34) * 100, 3);
    expect(percentOf(bars[1], 'left')).toBeCloseTo((29 / 34) * 100, 3);
    expect(percentOf(bars[1], 'width')).toBeCloseTo((5 / 34) * 100, 3);
  });

  it('keeps a one-calendar-day version visible', () => {
    render(
      <TimelineGantt
        versions={[
          version({
            versionNumber: 2,
            lifecycleStatus: 'DRAFT',
            startDate: '2026-11-02',
            endDate: '2026-11-02',
            durationWorkdays: 1,
          }),
        ]}
      />,
    );

    const bar = within(ganttGroup()).getByRole('img', { name: /^v2, Draft/ });
    expect(percentOf(bar, 'left')).toBeCloseTo(0, 3);
    expect(percentOf(bar, 'width')).toBeCloseTo(100, 3);
  });

  it('shows an empty state and ignores keyboard events without versions', () => {
    render(<TimelineGantt versions={[]} />);

    expect(screen.getByText('No versions yet.')).toBeInTheDocument();
    const group = ganttGroup();
    expect(group).not.toHaveAttribute('tabindex');
    fireEvent.keyDown(group, { key: 'ArrowDown' });
    expect(within(group).queryAllByRole('img')).toHaveLength(0);
  });

  it('keeps a single tab stop and moves focus across bars with arrow keys', async () => {
    const user = userEvent.setup();
    render(<TimelineGantt versions={[version({ versionNumber: 1 }), version({ versionNumber: 2, lifecycleStatus: 'DRAFT' })]} />);

    const group = ganttGroup();
    const first = within(group).getByRole('img', { name: /^v1,/ });
    const last = within(group).getByRole('img', { name: /^v2,/ });

    await user.tab();
    expect(group).toHaveFocus();
    // ArrowUp on the container itself does nothing yet.
    await user.keyboard('{ArrowUp}');
    expect(group).toHaveFocus();

    await user.keyboard('{ArrowDown}');
    expect(first).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(last).toHaveFocus();
    await user.keyboard('{ArrowUp}');
    expect(first).toHaveFocus();
    // Cannot move before the first row.
    await user.keyboard('{ArrowUp}');
    expect(first).toHaveFocus();
    await user.keyboard('{End}');
    expect(last).toHaveFocus();
    // Home jumps back to the first row.
    await user.keyboard('{Home}');
    expect(first).toHaveFocus();
    // Cannot move past the last row.
    await user.keyboard('{ArrowDown}');
    expect(last).toHaveFocus();
    // Unsupported keys leave focus untouched.
    await user.keyboard('{x}');
    expect(last).toHaveFocus();
  });
});
