import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { AxiosError } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/client', () => ({
  userApi: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
  taskApi: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
  timelineApi: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
  authApi: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}));

import { authApi, taskApi, timelineApi, userApi } from '@/api/client';
import {
  getProject,
  listProjectMembers,
  listProjects,
  useAddMembers,
  useChangeProjectStatus,
  useCreateProject,
  useProject,
  useProjectMembers,
  useProjects,
  useRemoveMember,
  useUpdateProject,
} from '@/features/projects/api';
import {
  getTask,
  listTasks,
  useChangeTaskProgress,
  useChangeTaskStatus,
  useCreateTask,
  useDeleteTask,
  useTasks,
  useUpdateTask,
} from '@/features/tasks/api';
import {
  getTimeline,
  useCancelTimelineDraft,
  useChangeTimelineProgress,
  useChangeTimelineStatus,
  useCreateTimeline,
  useCreateTimelineRevision,
  useEditTimelineDraft,
  usePublishTimeline,
  useTimeline,
  useTimelineVersions,
} from '@/features/timeline/api';
import {
  listUsers,
  setUserPassword,
  changeOwnPassword,
  useChangeUserStatus,
  useCreateUser,
  useReplaceRoles,
  useRoles,
  useSetUserPassword,
  useUpdateUser,
  useUsers,
} from '@/features/users/api';
import {
  useCreateHoliday,
  useDeleteHoliday,
  useHolidayHorizon,
  useHolidays,
  useUpdateHoliday,
} from '@/features/holidays/api';

const mockUser = vi.mocked(userApi);
const mockTask = vi.mocked(taskApi);
const mockTimeline = vi.mocked(timelineApi);
const mockAuth = vi.mocked(authApi);

const project = {
  id: 'p1',
  code: 'P1',
  name: 'Project',
  description: 'Desc',
  startDate: '2026-01-01',
  endDate: '2026-02-01',
  status: 'ACTIVE' as const,
  memberCount: 2,
  createdBy: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};
const page = <T,>(content: T[]) => ({
  content,
  page: 0,
  size: 10,
  totalElements: content.length,
  totalPages: 1,
  first: true,
  last: true,
});
const task = {
  id: 't1',
  projectId: 'p1',
  title: 'Task',
  startDate: '2026-01-01',
  endDate: '2026-01-02',
  durationDays: 2,
  pic: { userId: 'u2', name: 'Pic' },
  status: 'OPEN' as const,
  percentComplete: 10,
};
const timelineVersion = {
  id: 'v1',
  timelineId: 'tl1',
  projectId: 'p1',
  versionNumber: 1,
  lifecycleStatus: 'DRAFT' as const,
  startDate: '2026-01-01',
  endDate: '2026-01-31',
  durationWorkdays: 21,
  operationalStatus: 'OPEN' as const,
  percentActual: 0,
  percentPlan: 50,
  planAsOfDate: '2026-01-15',
  createdBy: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};
const timeline = {
  timelineId: 'tl1',
  projectId: 'p1',
  versionCount: 1,
  current: null,
  draft: timelineVersion,
};
const holiday = {
  id: 'h1',
  date: '2026-08-17',
  name: 'Independence Day',
  type: 'NATIONAL' as const,
  createdBy: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};
const user = {
  id: 'u1',
  username: 'jane',
  email: 'jane@example.com',
  fullName: 'Jane Doe',
  enabled: true,
  roles: ['USER' as const],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { queryClient, wrapper };
}

function notFound(code: string) {
  return new AxiosError('request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    data: { code },
    status: code === 'TIMELINE_NOT_FOUND' ? 404 : 500,
    statusText: '',
    headers: {},
    config: {} as never,
  });
}

describe('project API', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lists, loads, and mutates projects', async () => {
    mockUser.get.mockResolvedValue({ data: page([project]) } as never);
    await expect(listProjects({ page: 0, size: 10 })).resolves.toEqual(page([project]));
    expect(mockUser.get).toHaveBeenCalledWith('/api/v1/projects', {
      params: { page: 0, size: 10, sort: 'createdAt,desc' },
    });

    mockUser.get.mockResolvedValue({ data: project } as never);
    await expect(getProject('p1')).resolves.toEqual(project);
    expect(mockUser.get).toHaveBeenCalledWith('/api/v1/projects/p1');

    mockUser.get.mockResolvedValue({ data: page([{ userId: 'u1' }]) } as never);
    await expect(listProjectMembers('p1')).resolves.toEqual(page([{ userId: 'u1' }]));
    expect(mockUser.get).toHaveBeenCalledWith('/api/v1/projects/p1/members', {
      params: { page: 0, size: 100, sort: 'addedAt,asc' },
    });

    const { wrapper } = setup();
    const projectsHook = renderHook(() => useProjects({ page: 0, size: 10 }), { wrapper });
    await waitFor(() => expect(projectsHook.result.current.isSuccess).toBe(true));

    mockUser.get.mockResolvedValue({ data: project } as never);
    const projectHook = renderHook(() => useProject('p1'), { wrapper });
    await waitFor(() => expect(projectHook.result.current.data).toEqual(project));
    const disabledHook = renderHook(() => useProject(undefined), { wrapper });
    expect(disabledHook.result.current.fetchStatus).toBe('idle');

    const membersHook = renderHook(() => useProjectMembers('p1'), { wrapper });
    await waitFor(() => expect(membersHook.result.current.isSuccess).toBe(true));

    mockUser.post.mockResolvedValue({ data: project } as never);
    const create = renderHook(() => useCreateProject(), { wrapper });
    await act(async () => {
      await create.result.current.mutateAsync({
        code: 'P1',
        name: 'Project',
        startDate: '2026-01-01',
        endDate: '2026-02-01',
        memberIds: ['u1'],
      });
    });
    expect(mockUser.post).toHaveBeenCalledWith(
      '/api/v1/projects',
      expect.objectContaining({ code: 'P1', requestId: expect.any(String) }),
    );

    mockUser.put.mockResolvedValue({ data: project } as never);
    const update = renderHook(() => useUpdateProject('p1'), { wrapper });
    await act(async () => {
      await update.result.current.mutateAsync({
        name: 'Renamed',
        startDate: '2026-01-01',
        endDate: '2026-02-01',
      });
    });
    expect(mockUser.put).toHaveBeenCalledWith('/api/v1/projects/p1', {
      name: 'Renamed',
      startDate: '2026-01-01',
      endDate: '2026-02-01',
    });

    mockUser.patch.mockResolvedValue({ data: project } as never);
    const status = renderHook(() => useChangeProjectStatus(), { wrapper });
    await act(async () => {
      await status.result.current.mutateAsync({ projectId: 'p1', status: 'ARCHIVED' });
    });
    expect(mockUser.patch).toHaveBeenCalledWith('/api/v1/projects/p1/status', {
      status: 'ARCHIVED',
    });

    mockUser.post.mockResolvedValue({ data: { added: 1, alreadyMembers: 0 } } as never);
    const add = renderHook(() => useAddMembers('p1'), { wrapper });
    await act(async () => {
      await add.result.current.mutateAsync(['u2']);
    });
    expect(mockUser.post).toHaveBeenCalledWith('/api/v1/projects/p1/members', {
      userIds: ['u2'],
    });

    mockUser.delete.mockResolvedValue({ data: undefined } as never);
    const remove = renderHook(() => useRemoveMember('p1'), { wrapper });
    await act(async () => {
      await remove.result.current.mutateAsync('u2');
    });
    expect(mockUser.delete).toHaveBeenCalledWith('/api/v1/projects/p1/members/u2');
  });
});

describe('idempotency', () => {
  beforeEach(() => vi.resetAllMocks());

  it('reuses the requestId when a failed create is retried manually', async () => {
    const { wrapper } = setup();
    vi.spyOn(crypto, 'randomUUID').mockReturnValueOnce('11111111-1111-4111-8111-111111111111');
    mockUser.post
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ data: project } as never);
    const create = renderHook(() => useCreateProject(), { wrapper });
    const input = {
      code: 'P1',
      name: 'Project',
      startDate: '2026-01-01',
      endDate: '2026-02-01',
    };
    await act(async () => {
      await expect(create.result.current.mutateAsync(input)).rejects.toThrow('network');
    });
    await act(async () => {
      await create.result.current.mutateAsync(input);
    });
    const first = mockUser.post.mock.calls[0][1] as { requestId: string };
    const second = mockUser.post.mock.calls[1][1] as { requestId: string };
    expect(first.requestId).toBe(second.requestId);
  });
});

describe('task API', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lists, loads, and mutates tasks', async () => {
    mockTask.get.mockResolvedValue({ data: page([task]) } as never);
    await expect(listTasks('p1', { page: 0, size: 10 })).resolves.toEqual(page([task]));
    expect(mockTask.get).toHaveBeenCalledWith('/api/v1/projects/p1/tasks', {
      params: { page: 0, size: 10, sort: 'createdAt,desc' },
    });
    mockTask.get.mockResolvedValue({ data: task } as never);
    await expect(getTask('p1', 't1')).resolves.toEqual(task);

    const { wrapper } = setup();
    const hook = renderHook(() => useTasks('p1', { page: 0, size: 10 }), { wrapper });
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true));

    const input = {
      title: 'Task',
      startDate: '2026-01-01',
      endDate: '2026-01-02',
      picUserId: 'u2',
      status: 'OPEN' as const,
      percentComplete: 10,
    };
    mockTask.post.mockResolvedValue({ data: task } as never);
    const create = renderHook(() => useCreateTask('p1'), { wrapper });
    await act(async () => {
      await create.result.current.mutateAsync(input);
    });
    expect(mockTask.post).toHaveBeenCalledWith(
      '/api/v1/projects/p1/tasks',
      expect.objectContaining({ requestId: expect.any(String) }),
    );

    mockTask.put.mockResolvedValue({ data: task } as never);
    const update = renderHook(() => useUpdateTask('p1', 't1'), { wrapper });
    await act(async () => {
      await update.result.current.mutateAsync(input);
    });
    expect(mockTask.put).toHaveBeenCalledWith('/api/v1/projects/p1/tasks/t1', input);

    mockTask.patch.mockResolvedValue({ data: task } as never);
    const status = renderHook(() => useChangeTaskStatus('p1'), { wrapper });
    await act(async () => {
      await status.result.current.mutateAsync({ taskId: 't1', status: 'CLOSED' });
    });
    expect(mockTask.patch).toHaveBeenCalledWith('/api/v1/projects/p1/tasks/t1/status', {
      status: 'CLOSED',
    });

    const progress = renderHook(() => useChangeTaskProgress('p1'), { wrapper });
    await act(async () => {
      await progress.result.current.mutateAsync({ taskId: 't1', percentComplete: 90 });
    });
    expect(mockTask.patch).toHaveBeenCalledWith('/api/v1/projects/p1/tasks/t1/progress', {
      percentComplete: 90,
    });

    mockTask.delete.mockResolvedValue({ data: undefined } as never);
    const remove = renderHook(() => useDeleteTask('p1'), { wrapper });
    await act(async () => {
      await remove.result.current.mutateAsync('t1');
    });
    expect(mockTask.delete).toHaveBeenCalledWith('/api/v1/projects/p1/tasks/t1');
  });
});

describe('timeline API', () => {
  beforeEach(() => vi.resetAllMocks());

  it('handles aggregate presence, absence, and failures', async () => {
    mockTimeline.get.mockResolvedValue({ data: timeline } as never);
    await expect(getTimeline('p1')).resolves.toEqual(timeline);
    mockTimeline.get.mockRejectedValue(notFound('TIMELINE_NOT_FOUND'));
    await expect(getTimeline('p1')).resolves.toBeNull();
    const other = notFound('SERVER_ERROR');
    mockTimeline.get.mockRejectedValue(other);
    await expect(getTimeline('p1')).rejects.toBe(other);

    mockTimeline.get.mockResolvedValue({ data: timeline } as never);
    const { wrapper } = setup();
    const aggregate = renderHook(() => useTimeline('p1'), { wrapper });
    await waitFor(() => expect(aggregate.result.current.isSuccess).toBe(true));

    mockTimeline.get.mockResolvedValue({ data: page([timelineVersion]) } as never);
    const versions = renderHook(() => useTimelineVersions('p1', 0), { wrapper });
    await waitFor(() => expect(versions.result.current.isSuccess).toBe(true));
    expect(mockTimeline.get).toHaveBeenCalledWith('/api/v1/projects/p1/timeline/versions', {
      params: { page: 0, size: 50, sort: 'versionNumber,desc' },
    });
  });

  it('runs create, edit, cancel, publish, revision, status, and progress mutations', async () => {
    const { wrapper } = setup();
    mockTimeline.post.mockResolvedValue({ data: timelineVersion } as never);
    mockTimeline.put.mockResolvedValue({ data: timelineVersion } as never);
    mockTimeline.delete.mockResolvedValue({ data: undefined } as never);
    mockTimeline.patch.mockResolvedValue({ data: timelineVersion } as never);

    const create = renderHook(() => useCreateTimeline('p1'), { wrapper });
    await act(async () => {
      await create.result.current.mutateAsync({
        startDate: '2026-01-01',
        endDate: '2026-01-31',
      });
    });
    expect(mockTimeline.post).toHaveBeenCalledWith(
      '/api/v1/projects/p1/timeline',
      expect.objectContaining({ requestId: expect.any(String) }),
    );

    const edit = renderHook(() => useEditTimelineDraft('p1'), { wrapper });
    await act(async () => {
      await edit.result.current.mutateAsync({
        startDate: '2026-01-02',
        endDate: '2026-01-30',
        expectedDraftUpdatedAt: '2026-01-01T00:00:00Z',
      });
    });
    expect(mockTimeline.put).toHaveBeenCalledWith('/api/v1/projects/p1/timeline/draft', {
      startDate: '2026-01-02',
      endDate: '2026-01-30',
      expectedDraftUpdatedAt: '2026-01-01T00:00:00Z',
    });

    const cancel = renderHook(() => useCancelTimelineDraft('p1'), { wrapper });
    await act(async () => {
      await cancel.result.current.mutateAsync('2026-01-01T00:00:00Z');
    });
    expect(mockTimeline.delete).toHaveBeenCalledWith('/api/v1/projects/p1/timeline/draft', {
      params: { expectedDraftUpdatedAt: '2026-01-01T00:00:00Z' },
    });

    const publish = renderHook(() => usePublishTimeline('p1'), { wrapper });
    await act(async () => {
      await publish.result.current.mutateAsync({
        expectedDraftUpdatedAt: '2026-01-01T00:00:00Z',
        expectedCurrentUpdatedAt: '2026-01-01T00:00:00Z',
      });
    });
    expect(mockTimeline.post).toHaveBeenCalledWith(
      '/api/v1/projects/p1/timeline/draft/publish',
      expect.objectContaining({ requestId: expect.any(String) }),
    );

    const revision = renderHook(() => useCreateTimelineRevision('p1'), { wrapper });
    await act(async () => {
      await revision.result.current.mutateAsync({ revisionNotes: 'Changed' });
    });
    expect(mockTimeline.post).toHaveBeenCalledWith(
      '/api/v1/projects/p1/timeline/revisions',
      expect.objectContaining({ revisionNotes: 'Changed', requestId: expect.any(String) }),
    );

    const status = renderHook(() => useChangeTimelineStatus('p1'), { wrapper });
    await act(async () => {
      await status.result.current.mutateAsync('CLOSED');
    });
    expect(mockTimeline.patch).toHaveBeenCalledWith(
      '/api/v1/projects/p1/timeline/current/status',
      { status: 'CLOSED' },
    );

    const progress = renderHook(() => useChangeTimelineProgress('p1'), { wrapper });
    await act(async () => {
      await progress.result.current.mutateAsync(75);
    });
    expect(mockTimeline.patch).toHaveBeenCalledWith(
      '/api/v1/projects/p1/timeline/current/progress',
      { percentActual: 75 },
    );
  });
});

describe('user API', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lists users and roles and manages passwords', async () => {
    mockUser.get.mockResolvedValue({ data: page([user]) } as never);
    await expect(listUsers({ page: 0, size: 10 })).resolves.toEqual(page([user]));
    expect(mockUser.get).toHaveBeenCalledWith('/api/v1/users', {
      params: { page: 0, size: 10, sort: 'fullName,asc' },
    });

    mockAuth.put.mockResolvedValue({ data: undefined } as never);
    await setUserPassword('u1', 'NewPass1');
    expect(mockAuth.put).toHaveBeenCalledWith('/api/v1/auth/users/u1/password', {
      newPassword: 'NewPass1',
    });
    await changeOwnPassword('OldPass1', 'NewPass1');
    expect(mockAuth.put).toHaveBeenCalledWith('/api/v1/auth/me/password', {
      currentPassword: 'OldPass1',
      newPassword: 'NewPass1',
    });

    const { wrapper } = setup();
    const hook = renderHook(
      () => useUsers({ page: 0, size: 10, enabled: true, role: 'ADMIN' }),
      { wrapper },
    );
    await waitFor(() => expect(hook.result.current.isSuccess).toBe(true));

    mockUser.get.mockResolvedValue({ data: [{ id: 'r1', name: 'USER', description: 'User' }] } as never);
    const roles = renderHook(() => useRoles(), { wrapper });
    await waitFor(() => expect(roles.result.current.data).toHaveLength(1));
    expect(mockUser.get).toHaveBeenCalledWith('/api/v1/roles');
  });

  it('runs user mutations and password reset mutation', async () => {
    const { wrapper } = setup();
    const input = { username: 'jane', email: 'jane@example.com', fullName: 'Jane', roles: ['USER' as const] };
    mockUser.post.mockResolvedValue({ data: user } as never);
    const create = renderHook(() => useCreateUser(), { wrapper });
    await act(async () => {
      await create.result.current.mutateAsync(input);
    });
    expect(mockUser.post).toHaveBeenCalledWith('/api/v1/users', input);

    mockUser.put.mockResolvedValue({ data: user } as never);
    const update = renderHook(() => useUpdateUser('u1'), { wrapper });
    await act(async () => {
      await update.result.current.mutateAsync({ email: 'new@example.com', fullName: 'New Name' });
    });
    expect(mockUser.put).toHaveBeenCalledWith('/api/v1/users/u1', {
      email: 'new@example.com',
      fullName: 'New Name',
    });

    mockUser.patch.mockResolvedValue({ data: user } as never);
    const status = renderHook(() => useChangeUserStatus(), { wrapper });
    await act(async () => {
      await status.result.current.mutateAsync({ userId: 'u1', enabled: false });
    });
    expect(mockUser.patch).toHaveBeenCalledWith('/api/v1/users/u1/status', { enabled: false });

    const replace = renderHook(() => useReplaceRoles('u1'), { wrapper });
    await act(async () => {
      await replace.result.current.mutateAsync(['ADMIN', 'USER']);
    });
    expect(mockUser.put).toHaveBeenCalledWith('/api/v1/users/u1/roles', {
      roles: ['ADMIN', 'USER'],
    });

    mockAuth.put.mockResolvedValue({ data: undefined } as never);
    const password = renderHook(() => useSetUserPassword(), { wrapper });
    await act(async () => {
      await password.result.current.mutateAsync({ userId: 'u1', newPassword: 'NewPass1' });
    });
    expect(mockAuth.put).toHaveBeenCalledWith('/api/v1/auth/users/u1/password', {
      newPassword: 'NewPass1',
    });
  });
});

describe('holiday API', () => {
  beforeEach(() => vi.resetAllMocks());

  it('reads the database-derived coverage horizon from response headers', async () => {
    const { wrapper } = setup();
    mockTimeline.get.mockResolvedValue({
      data: page([holiday]),
      headers: {
        'x-holiday-coverage-start': '2025-01-01',
        'x-holiday-coverage-end': '2027-12-31',
      },
    } as never);
    const horizon = renderHook(() => useHolidayHorizon(), { wrapper });
    await waitFor(() =>
      expect(horizon.result.current.data).toEqual({
        startDate: '2025-01-01',
        endDate: '2027-12-31',
      }),
    );
    expect(mockTimeline.get).toHaveBeenCalledWith('/api/v1/holidays', {
      params: { page: 0, size: 1, sort: 'date,asc' },
    });
  });

  it('lists holidays with and without a year and mutates them', async () => {
    mockTimeline.get.mockResolvedValue({ data: page([holiday]) } as never);
    const { wrapper } = setup();
    const withYear = renderHook(() => useHolidays(2026, 0), { wrapper });
    await waitFor(() => expect(withYear.result.current.isSuccess).toBe(true));
    expect(mockTimeline.get).toHaveBeenCalledWith('/api/v1/holidays', {
      params: { year: 2026, page: 0, size: 20, sort: 'date,asc' },
    });

    const withoutYear = renderHook(() => useHolidays(null, 1), { wrapper });
    await waitFor(() => expect(withoutYear.result.current.isSuccess).toBe(true));
    expect(mockTimeline.get).toHaveBeenCalledWith('/api/v1/holidays', {
      params: { year: undefined, page: 1, size: 20, sort: 'date,asc' },
    });

    mockTimeline.post.mockResolvedValue({ data: holiday } as never);
    const create = renderHook(() => useCreateHoliday(), { wrapper });
    await act(async () => {
      await create.result.current.mutateAsync({
        date: '2026-08-17',
        name: 'Independence Day',
        type: 'NATIONAL',
      });
    });
    expect(mockTimeline.post).toHaveBeenCalledWith('/api/v1/holidays', {
      date: '2026-08-17',
      name: 'Independence Day',
      type: 'NATIONAL',
    });

    mockTimeline.put.mockResolvedValue({ data: holiday } as never);
    const update = renderHook(() => useUpdateHoliday(), { wrapper });
    await act(async () => {
      await update.result.current.mutateAsync({
        id: 'h1',
        input: { date: '2026-08-17', name: 'Updated', type: 'JOINT_LEAVE' },
      });
    });
    expect(mockTimeline.put).toHaveBeenCalledWith('/api/v1/holidays/h1', {
      date: '2026-08-17',
      name: 'Updated',
      type: 'JOINT_LEAVE',
    });

    mockTimeline.delete.mockResolvedValue({ data: undefined } as never);
    const remove = renderHook(() => useDeleteHoliday(), { wrapper });
    await act(async () => {
      await remove.result.current.mutateAsync('h1');
    });
    expect(mockTimeline.delete).toHaveBeenCalledWith('/api/v1/holidays/h1');
  });
});
