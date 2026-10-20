import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { timelineApi } from '@/api/client';
import { problemCode } from '@/lib/errors';
import { queryKeys } from '@/lib/queryKeys';
import type {
  PageResponse,
  TimelineAggregate,
  TimelineVersion,
  OperationalStatus,
} from '@/types/api';

export async function getTimeline(projectId: string): Promise<TimelineAggregate | null> {
  try {
    const { data } = await timelineApi.get<TimelineAggregate>(
      `/api/v1/projects/${projectId}/timeline`,
    );
    return data;
  } catch (error) {
    if (problemCode(error) === 'TIMELINE_NOT_FOUND') return null;
    throw error;
  }
}

export function useTimeline(projectId: string) {
  return useQuery({
    queryKey: queryKeys.timeline.aggregate(projectId),
    queryFn: () => getTimeline(projectId),
  });
}

export function useTimelineVersions(projectId: string, page: number) {
  return useQuery({
    queryKey: queryKeys.timeline.versions(projectId, page),
    queryFn: async () => {
      const { data } = await timelineApi.get<PageResponse<TimelineVersion>>(
        `/api/v1/projects/${projectId}/timeline/versions`,
        { params: { page, size: 10, sort: 'versionNumber,desc' } },
      );
      return data;
    },
  });
}

function useTimelineMutation<TVariables>(
  projectId: string,
  mutationFn: (variables: TVariables) => Promise<unknown>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'timeline'] }),
  });
}

export function useCreateTimeline(projectId: string) {
  return useTimelineMutation(
    projectId,
    async (input: { startDate: string; endDate: string; revisionNotes?: string }) => {
      const { data } = await timelineApi.post<TimelineVersion>(
        `/api/v1/projects/${projectId}/timeline`,
        { ...input, requestId: crypto.randomUUID() },
      );
      return data;
    },
  );
}

export function useEditTimelineDraft(projectId: string) {
  return useTimelineMutation(
    projectId,
    async (input: {
      startDate: string;
      endDate: string;
      revisionNotes?: string;
      expectedDraftUpdatedAt: string;
    }) => {
      const { data } = await timelineApi.put<TimelineVersion>(
        `/api/v1/projects/${projectId}/timeline/draft`,
        input,
      );
      return data;
    },
  );
}

export function useCancelTimelineDraft(projectId: string) {
  return useTimelineMutation(projectId, async (expectedDraftUpdatedAt: string) => {
    await timelineApi.delete(`/api/v1/projects/${projectId}/timeline/draft`, {
      params: { expectedDraftUpdatedAt },
    });
  });
}

export function usePublishTimeline(projectId: string) {
  return useTimelineMutation(
    projectId,
    async (input: {
      expectedDraftUpdatedAt: string;
      expectedCurrentUpdatedAt?: string;
    }) => {
      const { data } = await timelineApi.post<TimelineVersion>(
        `/api/v1/projects/${projectId}/timeline/draft/publish`,
        { ...input, requestId: crypto.randomUUID() },
      );
      return data;
    },
  );
}

export function useCreateTimelineRevision(projectId: string) {
  return useTimelineMutation(
    projectId,
    async (input: { startDate?: string; endDate?: string; revisionNotes?: string }) => {
      const { data } = await timelineApi.post<TimelineVersion>(
        `/api/v1/projects/${projectId}/timeline/revisions`,
        { ...input, requestId: crypto.randomUUID() },
      );
      return data;
    },
  );
}

export function useChangeTimelineStatus(projectId: string) {
  return useTimelineMutation(projectId, async (status: OperationalStatus) => {
    const { data } = await timelineApi.patch<TimelineVersion>(
      `/api/v1/projects/${projectId}/timeline/current/status`,
      { status },
    );
    return data;
  });
}

export function useChangeTimelineProgress(projectId: string) {
  return useTimelineMutation(projectId, async (percentActual: number) => {
    const { data } = await timelineApi.patch<TimelineVersion>(
      `/api/v1/projects/${projectId}/timeline/current/progress`,
      { percentActual },
    );
    return data;
  });
}
