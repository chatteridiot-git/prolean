import { App } from 'antd';
import { render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const m = vi.hoisted(() => ({
  useAuth: vi.fn(),
  useProjectContext: vi.fn(),
  useProjects: vi.fn(),
  useCreateProject: vi.fn(),
  useChangeProjectStatus: vi.fn(),
  useProjectMembers: vi.fn(),
  useAddMembers: vi.fn(),
  useRemoveMember: vi.fn(),
  useTasks: vi.fn(),
  useCreateTask: vi.fn(),
  useUpdateTask: vi.fn(),
  useChangeTaskStatus: vi.fn(),
  useChangeTaskProgress: vi.fn(),
  useDeleteTask: vi.fn(),
  getTask: vi.fn(),
  useTimeline: vi.fn(),
  useTimelineVersions: vi.fn(),
  useCreateTimeline: vi.fn(),
  useEditTimelineDraft: vi.fn(),
  useCancelTimelineDraft: vi.fn(),
  usePublishTimeline: vi.fn(),
  useCreateTimelineRevision: vi.fn(),
  useChangeTimelineStatus: vi.fn(),
  useChangeTimelineProgress: vi.fn(),
  useHolidays: vi.fn(),
  useHolidayHorizon: vi.fn(),
  useCreateHoliday: vi.fn(),
  useUpdateHoliday: vi.fn(),
  useDeleteHoliday: vi.fn(),
}));

vi.mock('@/auth/AuthProvider', () => ({ useAuth: m.useAuth }));
vi.mock('@/app/ProjectLayout', () => ({ useProjectContext: m.useProjectContext }));
vi.mock('@/features/projects/api', () => ({
  useProjects: m.useProjects,
  useCreateProject: m.useCreateProject,
  useChangeProjectStatus: m.useChangeProjectStatus,
  useProjectMembers: m.useProjectMembers,
  useAddMembers: m.useAddMembers,
  useRemoveMember: m.useRemoveMember,
}));
vi.mock('@/features/tasks/api', () => ({
  useTasks: m.useTasks,
  useCreateTask: m.useCreateTask,
  useUpdateTask: m.useUpdateTask,
  useChangeTaskStatus: m.useChangeTaskStatus,
  useChangeTaskProgress: m.useChangeTaskProgress,
  useDeleteTask: m.useDeleteTask,
  getTask: m.getTask,
}));
vi.mock('@/features/timeline/api', () => ({
  useTimeline: m.useTimeline,
  useTimelineVersions: m.useTimelineVersions,
  useCreateTimeline: m.useCreateTimeline,
  useEditTimelineDraft: m.useEditTimelineDraft,
  useCancelTimelineDraft: m.useCancelTimelineDraft,
  usePublishTimeline: m.usePublishTimeline,
  useCreateTimelineRevision: m.useCreateTimelineRevision,
  useChangeTimelineStatus: m.useChangeTimelineStatus,
  useChangeTimelineProgress: m.useChangeTimelineProgress,
}));
vi.mock('@/features/holidays/api', () => ({
  useHolidays: m.useHolidays,
  useHolidayHorizon: m.useHolidayHorizon,
  useCreateHoliday: m.useCreateHoliday,
  useUpdateHoliday: m.useUpdateHoliday,
  useDeleteHoliday: m.useDeleteHoliday,
}));
vi.mock('@/features/timeline/TimelineFormDrawer', () => ({ TimelineFormDrawer: () => null }));

import { MembersPage } from '@/pages/MembersPage';
import { ProjectsPage } from '@/pages/ProjectsPage';
import { TasksPage } from '@/pages/TasksPage';
import { TimelinePage } from '@/pages/TimelinePage';
import { HolidaysPage } from '@/pages/HolidaysPage';
import type { Project, ProjectMember, ProjectSummary, TaskSummary, TimelineAggregate } from '@/types/api';

const mutation = () => ({ mutateAsync: vi.fn(), isPending: false, error: null });
const query = <T,>(data: T) => ({ data, isLoading: false, error: null, refetch: vi.fn() });

const activeProject: Project = {
  id: 'p1',
  code: 'ALPHA',
  name: 'Alpha',
  description: '',
  startDate: '2026-01-01',
  endDate: '2026-12-31',
  status: 'ACTIVE',
  memberCount: 1,
  createdBy: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const archivedProject: Project = { ...activeProject, status: 'ARCHIVED' };

const projectSummary: ProjectSummary = {
  id: 'p1',
  code: 'ALPHA',
  name: 'Alpha',
  startDate: '2026-01-01',
  endDate: '2026-12-31',
  status: 'ARCHIVED',
  memberCount: 2,
};

const member: ProjectMember = {
  userId: 'u1',
  username: 'jane',
  fullName: 'Jane Doe',
  email: 'jane@example.com',
  addedAt: '2026-01-01T00:00:00Z',
};

const task: TaskSummary = {
  id: 't1',
  projectId: 'p1',
  title: 'Design',
  startDate: '2026-01-01',
  endDate: '2026-01-04',
  durationDays: 4,
  pic: { userId: 'u1', name: 'Jane Doe' },
  status: 'OPEN',
  percentComplete: 0,
};

const published = {
  id: 'v1',
  timelineId: 'tl1',
  projectId: 'p1',
  versionNumber: 1,
  lifecycleStatus: 'PUBLISHED' as const,
  startDate: '2026-11-02',
  endDate: '2026-11-06',
  durationWorkdays: 5,
  operationalStatus: 'OPEN' as const,
  percentActual: 10,
  percentPlan: 20,
  planAsOfDate: '2026-11-02',
  updatedAt: '2026-11-02T00:00:00Z',
  createdAt: '2026-11-02T00:00:00Z',
  createdBy: 'u1',
};

const draft = {
  ...published,
  id: 'v2',
  versionNumber: 2,
  lifecycleStatus: 'DRAFT' as const,
  updatedAt: '2026-11-03T00:00:00.123456Z',
};

const timeline: TimelineAggregate = {
  timelineId: 'tl1',
  projectId: 'p1',
  versionCount: 2,
  current: published,
  draft,
};

function wrap(node: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <App>
        <MemoryRouter>{node}</MemoryRouter>
      </App>
    </QueryClientProvider>,
  );
}

describe('page flows', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    m.useAuth.mockReturnValue({ isAdmin: false, user: { id: 'u2' } });
    m.useProjectContext.mockReturnValue({ project: activeProject });
    m.useProjects.mockReturnValue(query({ content: [projectSummary], totalElements: 1 }));
    m.useCreateProject.mockReturnValue(mutation());
    m.useChangeProjectStatus.mockReturnValue(mutation());
    m.useProjectMembers.mockReturnValue(query({ content: [member], totalElements: 1 }));
    m.useAddMembers.mockReturnValue(mutation());
    m.useRemoveMember.mockReturnValue(mutation());
    m.useTasks.mockReturnValue(query({ content: [task], totalElements: 1 }));
    m.useCreateTask.mockReturnValue(mutation());
    m.useUpdateTask.mockReturnValue(mutation());
    m.useChangeTaskStatus.mockReturnValue(mutation());
    m.useChangeTaskProgress.mockReturnValue(mutation());
    m.useDeleteTask.mockReturnValue(mutation());
    m.useTimeline.mockReturnValue(query(timeline));
    m.useTimelineVersions.mockReturnValue(query({ content: [draft, published], totalElements: 2 }));
    m.useCreateTimeline.mockReturnValue(mutation());
    m.useEditTimelineDraft.mockReturnValue(mutation());
    m.useCancelTimelineDraft.mockReturnValue(mutation());
    m.usePublishTimeline.mockReturnValue(mutation());
    m.useCreateTimelineRevision.mockReturnValue(mutation());
    m.useChangeTimelineStatus.mockReturnValue(mutation());
    m.useChangeTimelineProgress.mockReturnValue(mutation());
    m.useHolidays.mockReturnValue(query({ content: [], totalElements: 0 }));
    m.useHolidayHorizon.mockReturnValue(query({ startDate: '2025-01-01', endDate: '2027-12-31' }));
    m.useCreateHoliday.mockReturnValue(mutation());
    m.useUpdateHoliday.mockReturnValue(mutation());
    m.useDeleteHoliday.mockReturnValue(mutation());
  });

  it('lists projects and keeps non-admin creation unavailable', () => {
    wrap(<ProjectsPage />);
    expect(screen.getByText('Alpha')).toBeInTheDocument();
    expect(screen.getByText('Archived')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /new project/i })).not.toBeInTheDocument();
  });

  it('makes an archived project read-only on tasks and members', () => {
    m.useProjectContext.mockReturnValue({ project: archivedProject });
    const tasks = wrap(<TasksPage />);
    expect(screen.getByText('This project is archived, so task editing is disabled.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /new task/i })).toBeDisabled();
    tasks.unmount();

    wrap(<MembersPage />);
    expect(screen.getByText('This project is archived; membership is read-only.')).toBeInTheDocument();
  });

  it('shows the database-derived holiday horizon', () => {
    wrap(<HolidaysPage />);
    expect(screen.getByText('Covered horizon: 2025-01-01 to 2027-12-31')).toBeInTheDocument();
  });

  it('renders the version gantt on the timeline page while keeping history and conflicts intact', () => {
    wrap(<TimelinePage />);
    const group = screen.getByRole('group', { name: 'Timeline version gantt' });
    expect(within(group).getAllByRole('img')).toHaveLength(2);
    expect(
      within(group).getByRole('img', { name: /^v1, Published, Open/ }),
    ).toBeInTheDocument();
    expect(
      within(group).getByRole('img', { name: /^v2, Draft/ }),
    ).toBeInTheDocument();
    // The Gantt reads a dedicated page-0 slice, independent of history paging.
    expect(m.useTimelineVersions).toHaveBeenCalledWith('p1', 0);
    expect(screen.getByText('Version history')).toBeInTheDocument();

    // The draft conflict controls still work: publish is reachable from the draft card.
    expect(screen.getByRole('button', { name: /publish/i })).toBeEnabled();
  });

  it('shows expected and current timestamps and reapplies after a publish conflict', async () => {
    const user = userEvent.setup();
    const publish = mutation();
    publish.mutateAsync
      .mockRejectedValueOnce({
        isAxiosError: true,
        response: {
          status: 409,
          data: { code: 'DRAFT_MODIFIED', detail: 'Draft changed', expected: draft.updatedAt },
        },
      })
      .mockResolvedValueOnce({});
    m.usePublishTimeline.mockReturnValue(publish);
    const refreshed = { ...timeline, draft: { ...draft, updatedAt: '2026-11-04T00:00:00Z' } };
    const timelineQuery = query(timeline);
    timelineQuery.refetch.mockResolvedValue({ data: refreshed });
    m.useTimeline.mockReturnValue(timelineQuery);

    wrap(<TimelinePage />);
    await user.click(screen.getByRole('button', { name: /publish/i }));
    await waitFor(() =>
      expect(document.querySelector('.ant-modal-confirm-btns .ant-btn-primary')).toBeTruthy(),
    );
    await user.click(document.querySelector('.ant-modal-confirm-btns .ant-btn-primary') as HTMLElement);
    await waitFor(() => expect(publish.mutateAsync).toHaveBeenCalledTimes(1));

    expect(await screen.findByText('Timeline changed before publishing')).toBeInTheDocument();
    expect(screen.getByText(draft.updatedAt)).toBeInTheDocument();
    expect(screen.getByText('2026-11-04T00:00:00Z')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reload and reapply' }));
    await waitFor(() => expect(publish.mutateAsync).toHaveBeenCalledTimes(2));
    expect(publish.mutateAsync).toHaveBeenLastCalledWith({
      expectedDraftUpdatedAt: '2026-11-04T00:00:00Z',
      expectedCurrentUpdatedAt: published.updatedAt,
    });
  });
});
