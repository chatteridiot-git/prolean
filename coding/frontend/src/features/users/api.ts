import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi, userApi } from '@/api/client';
import { queryKeys } from '@/lib/queryKeys';
import type { PageResponse, Role, RoleName, User } from '@/types/api';

export interface UserFilters {
  page: number;
  size: number;
  q?: string;
  enabled?: boolean;
  role?: RoleName;
  sort?: string;
}

export interface UserInput {
  username: string;
  email: string;
  fullName: string;
  roles: RoleName[];
}

export async function listUsers(filters: UserFilters): Promise<PageResponse<User>> {
  const { data } = await userApi.get<PageResponse<User>>('/api/v1/users', {
    params: { ...filters, sort: filters.sort ?? 'fullName,asc' },
  });
  return data;
}

export function useUsers(filters: UserFilters) {
  return useQuery({
    queryKey: queryKeys.users.list(filters),
    queryFn: () => listUsers(filters),
  });
}

export function useRoles() {
  return useQuery({
    queryKey: queryKeys.users.roles,
    queryFn: async () => {
      const { data } = await userApi.get<Role[]>('/api/v1/roles');
      return data;
    },
  });
}

export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: UserInput) => {
      const { data } = await userApi.post<User>('/api/v1/users', input);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useUpdateUser(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: { email: string; fullName: string }) => {
      const { data } = await userApi.put<User>(`/api/v1/users/${userId}`, input);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useChangeUserStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ userId, enabled }: { userId: string; enabled: boolean }) => {
      const { data } = await userApi.patch<User>(`/api/v1/users/${userId}/status`, { enabled });
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

export function useReplaceRoles(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (roles: RoleName[]) => {
      const { data } = await userApi.put<User>(`/api/v1/users/${userId}/roles`, { roles });
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

export async function setUserPassword(userId: string, newPassword: string): Promise<void> {
  await authApi.put(`/api/v1/auth/users/${userId}/password`, { newPassword });
}

export function useSetUserPassword() {
  return useMutation({
    mutationFn: ({ userId, newPassword }: { userId: string; newPassword: string }) =>
      setUserPassword(userId, newPassword),
  });
}

export async function changeOwnPassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  await authApi.put('/api/v1/auth/me/password', { currentPassword, newPassword });
}
