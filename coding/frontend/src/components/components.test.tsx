import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AsyncState, ProblemAlert } from '@/components/AsyncState';
import { PageHeader } from '@/components/PageHeader';
import { PercentBar } from '@/components/PercentBar';
import { StatusTag } from '@/components/StatusTag';
import type { ProjectStatus } from '@/types/api';

function axiosError(data: Record<string, unknown>) {
  return { isAxiosError: true, response: { status: 409, data } };
}

describe('shared components', () => {
  it('renders status tags for every status family', () => {
    const values = [
      'ACTIVE',
      'ARCHIVED',
      'OPEN',
      'IN_PROGRESS',
      'CLOSED',
      'DRAFT',
      'PUBLISHED',
      'SUPERSEDED',
      'CANCELLED',
      'NATIONAL',
      'JOINT_LEAVE',
    ] as const;
    for (const value of values) {
      const { container, unmount } = render(<StatusTag value={value} />);
      expect(container.textContent).toBeTruthy();
      unmount();
    }
  });

  it('falls back to the raw value for unknown statuses', () => {
    render(<StatusTag value={'UNKNOWN' as ProjectStatus} />);
    expect(screen.getByText('UNKNOWN')).toBeInTheDocument();
  });

  it('renders page headers with and without subtitle and actions', () => {
    const { rerender } = render(
      <PageHeader title="Projects" subtitle="All projects" extra={<button>New</button>} />,
    );
    expect(screen.getByText('Projects')).toBeInTheDocument();
    expect(screen.getByText('All projects')).toBeInTheDocument();
    expect(screen.getByText('New')).toBeInTheDocument();

    rerender(<PageHeader title="Projects" />);
    expect(screen.queryByText('All projects')).not.toBeInTheDocument();
  });

  it('renders percent bars and labels', () => {
    const { rerender } = render(<PercentBar percent={150} status="CLOSED" />);
    expect(screen.getByText('100.00%')).toBeInTheDocument();
    rerender(<PercentBar percent={45.5} status="IN_PROGRESS" label="Actual" />);
    expect(screen.getByText('Actual')).toBeInTheDocument();
    expect(screen.getByText('45.50%')).toBeInTheDocument();
    rerender(<PercentBar percent={-10} status="OPEN" />);
    expect(screen.getByText('0.00%')).toBeInTheDocument();
  });

  it('handles loading, error, empty and content states', async () => {
    const user = userEvent.setup();
    const retry = vi.fn();
    const { container, rerender } = render(
      <AsyncState loading>content</AsyncState>,
    );
    expect(container.querySelector('.ant-skeleton')).toBeInTheDocument();

    rerender(
      <AsyncState loading={false} error={axiosError({ detail: 'Nope' })} onRetry={retry}>
        content
      </AsyncState>,
    );
    expect(screen.getByText('Unable to load data')).toBeInTheDocument();
    expect(screen.getByText('Nope')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(retry).toHaveBeenCalled();

    rerender(
      <AsyncState loading={false} empty emptyText="No projects">
        content
      </AsyncState>,
    );
    expect(screen.getByText('No projects')).toBeInTheDocument();

    rerender(
      <AsyncState loading={false} empty={false}>
        <span>Loaded</span>
      </AsyncState>,
    );
    expect(screen.getByText('Loaded')).toBeInTheDocument();
  });

  it('shows problem details, request IDs and actions', () => {
    render(
      <ProblemAlert
        error={axiosError({ detail: 'Conflict', requestId: 'req-1' })}
        title="Save failed"
        action={<button>Reload</button>}
      />,
    );
    expect(screen.getByText('Save failed')).toBeInTheDocument();
    expect(screen.getByText('Conflict')).toBeInTheDocument();
    expect(screen.getByText('Request ID: req-1')).toBeInTheDocument();
    expect(screen.getByText('Reload')).toBeInTheDocument();
  });

  it('shows the fallback error without request metadata', () => {
    render(<ProblemAlert error={new Error('boom')} />);
    expect(screen.getByText('Unable to load data')).toBeInTheDocument();
    expect(screen.queryByText(/Request ID/)).not.toBeInTheDocument();
  });
});
