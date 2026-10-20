import axios from 'axios';
import type { ProblemDetails } from '@/types/api';

export const EMPTY_PROBLEM: ProblemDetails = {
  title: 'Request failed',
  detail: 'The request could not be completed. Please try again.',
  code: 'NETWORK_ERROR',
};

export function problemFrom(error: unknown): ProblemDetails {
  if (axios.isAxiosError<ProblemDetails>(error) && error.response?.data) {
    return { ...EMPTY_PROBLEM, ...error.response.data };
  }
  return EMPTY_PROBLEM;
}

export function problemCode(error: unknown): string | undefined {
  return problemFrom(error).code;
}

export function problemMessage(error: unknown): string {
  const problem = problemFrom(error);
  return problem.detail || problem.title || EMPTY_PROBLEM.detail!;
}

export function fieldErrors(error: unknown): Record<string, string> {
  const problem = problemFrom(error);
  return Object.fromEntries(
    (problem.errors ?? []).map((item) => [item.field, item.message]),
  );
}

export function isConflict(error: unknown, code?: string): boolean {
  const problem = problemFrom(error);
  return problem.status === 409 && (code === undefined || problem.code === code);
}

export const CONFLICT_CODES = ['DRAFT_MODIFIED', 'CURRENT_VERSION_CHANGED'] as const;

export function isTimelineConflict(error: unknown): boolean {
  const code = problemCode(error);
  return CONFLICT_CODES.some((candidate) => candidate === code);
}
