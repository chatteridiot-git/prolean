import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { timelineApi } from '@/api/client';
import { queryKeys } from '@/lib/queryKeys';
import type { Holiday, HolidayType, PageResponse } from '@/types/api';

export interface HolidayInput {
  date: string;
  name: string;
  type: HolidayType;
}

export function useHolidays(year: number | null, page: number, size = 20) {
  return useQuery({
    queryKey: queryKeys.holidays.list(year, page),
    queryFn: async () => {
      const { data } = await timelineApi.get<PageResponse<Holiday>>('/api/v1/holidays', {
        params: { year: year ?? undefined, page, size, sort: 'date,asc' },
      });
      return data;
    },
  });
}

export function useCreateHoliday() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: HolidayInput) => {
      const { data } = await timelineApi.post<Holiday>('/api/v1/holidays', input);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['holidays'] }),
  });
}

export function useUpdateHoliday() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: HolidayInput }) => {
      const { data } = await timelineApi.put<Holiday>(`/api/v1/holidays/${id}`, input);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['holidays'] }),
  });
}

export function useDeleteHoliday() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await timelineApi.delete(`/api/v1/holidays/${id}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['holidays'] }),
  });
}
