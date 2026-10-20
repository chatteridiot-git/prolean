import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { userApi } from '@/api/client';
import { queryKeys } from '@/lib/queryKeys';
import type {
  MembersAddedResponse,
  PageResponse,
  Project,
  ProjectMember,
  ProjectStatus,
  ProjectSummary,
} from '@/types/api';

export interface ProjectFilters {
  page: number;
  size: number;
  q?: string;
  status?: ProjectStatus;
  sort?: string;
}

export interface ProjectInput {
  code: string;
  name: string;
  description?: string;
  startDate: string;
  endDate: string;
  memberIds?: string[];
}

export async function listProjects(filters: ProjectFilters): Promise<PageResponse<ProjectSummary>> {
  const { data } = await userApi.get<PageResponse<ProjectSummary>>('/api/v1/projects', {
    params: { ...filters, sort: filters.sort ?? 'createdAt,desc' },
  });
  return data;
}

export async function getProject(projectId: string): Promise<Project> {
  const { data } = await userApi.get<Project>(`/api/v1/projects/${projectId}`);
  return data;
}

export function useProjects(filters: ProjectFilters) {
  return useQuery({
    queryKey: queryKeys.projects.list(filters),
    queryFn: () => listProjects(filters),
  });
}

export function useProject(projectId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.projects.detail(projectId ?? ''),
    queryFn: () => getProject(projectId!),
    enabled: Boolean(projectId),
  });
}

export function useCreateProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: ProjectInput) => {
      const { data } = await userApi.post<Project>('/api/v1/projects', {
        ...input,
        requestId: crypto.randomUUID(),
      });
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.projects.all }),
  });
}

export function useUpdateProject(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: Omit<ProjectInput, 'code' | 'memberIds'>) => {
      const { data } = await userApi.put<Project>(`/api/v1/projects/${projectId}`, input);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.projects.all }),
  });
}

export function useChangeProjectStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ projectId, status }: { projectId: string; status: ProjectStatus }) => {
      const { data } = await userApi.patch<Project>(`/api/v1/projects/${projectId}/status`, {
        status,
      });
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.projects.all }),
  });
}

export async function listProjectMembers(
  projectId: string,
  page = 0,
  size = 100,
): Promise<PageResponse<ProjectMember>> {
  const { data } = await userApi.get<PageResponse<ProjectMember>>(
    `/api/v1/projects/${projectId}/members`,
    { params: { page, size, sort: 'fullName,asc' } },
  );
  return data;
}

export function useProjectMembers(projectId: string, page = 0) {
  return useQuery({
    queryKey: queryKeys.projects.members(projectId, page),
    queryFn: () => listProjectMembers(projectId, page),
  });
}

export function useAddMembers(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (userIds: string[]) => {
      const { data } = await userApi.post<MembersAddedResponse>(
        `/api/v1/projects/${projectId}/members`,
        { userIds },
      );
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.projects.all }),
  });
}

export function useRemoveMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (userId: string) => {
      await userApi.delete(`/api/v1/projects/${projectId}/members/${userId}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.projects.all }),
  });
}
