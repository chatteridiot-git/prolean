import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { taskApi } from '@/api/client';
import { queryKeys } from '@/lib/queryKeys';
import { useIdempotencyKey } from '@/lib/requestId';
import type { PageResponse, Task, TaskStatus, TaskSummary } from '@/types/api';

export interface TaskFilters {
  page: number;
  size: number;
  q?: string;
  status?: TaskStatus;
  picUserId?: string;
  sort?: string;
}

export interface TaskInput {
  title: string;
  description?: string;
  startDate: string;
  endDate: string;
  picUserId: string;
  status: TaskStatus;
  percentComplete: number;
}

export async function listTasks(
  projectId: string,
  filters: TaskFilters,
): Promise<PageResponse<TaskSummary>> {
  const { data } = await taskApi.get<PageResponse<TaskSummary>>(
    `/api/v1/projects/${projectId}/tasks`,
    { params: { ...filters, sort: filters.sort ?? 'createdAt,desc' } },
  );
  return data;
}

export function useTasks(projectId: string, filters: TaskFilters) {
  return useQuery({
    queryKey: queryKeys.tasks.list(projectId, filters),
    queryFn: () => listTasks(projectId, filters),
  });
}

export async function getTask(projectId: string, taskId: string): Promise<Task> {
  const { data } = await taskApi.get<Task>(`/api/v1/projects/${projectId}/tasks/${taskId}`);
  return data;
}

export function useCreateTask(projectId: string) {
  const queryClient = useQueryClient();
  const requestId = useIdempotencyKey();
  return useMutation({
    mutationFn: async (input: TaskInput) => {
      const { data } = await taskApi.post<Task>(`/api/v1/projects/${projectId}/tasks`, {
        ...input,
        requestId: requestId.get(),
      });
      return data;
    },
    onSuccess: () => {
      requestId.reset();
      return queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'tasks'] });
    },
  });
}

export function useUpdateTask(projectId: string, taskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: TaskInput) => {
      const { data } = await taskApi.put<Task>(
        `/api/v1/projects/${projectId}/tasks/${taskId}`,
        input,
      );
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'tasks'] }),
  });
}

export function useChangeTaskStatus(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ taskId, status }: { taskId: string; status: TaskStatus }) => {
      const { data } = await taskApi.patch<Task>(
        `/api/v1/projects/${projectId}/tasks/${taskId}/status`,
        { status },
      );
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'tasks'] }),
  });
}

export function useChangeTaskProgress(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ taskId, percentComplete }: { taskId: string; percentComplete: number }) => {
      const { data } = await taskApi.patch<Task>(
        `/api/v1/projects/${projectId}/tasks/${taskId}/progress`,
        { percentComplete },
      );
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'tasks'] }),
  });
}

export function useDeleteTask(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (taskId: string) => {
      await taskApi.delete(`/api/v1/projects/${projectId}/tasks/${taskId}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'tasks'] }),
  });
}
