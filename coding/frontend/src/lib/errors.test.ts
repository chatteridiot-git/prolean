import { describe, expect, it } from 'vitest';
import {
  CONFLICT_CODES,
  EMPTY_PROBLEM,
  fieldErrors,
  isConflict,
  isTimelineConflict,
  problemCode,
  problemFrom,
  problemMessage,
} from '@/lib/errors';

function axiosError(data: unknown, status = 400) {
  return {
    isAxiosError: true,
    response: { status, data },
  };
}

describe('problem details helpers', () => {
  it('falls back for unknown and empty errors', () => {
    expect(problemFrom(new Error('boom'))).toEqual(EMPTY_PROBLEM);
    expect(problemFrom(axiosError(undefined))).toEqual(EMPTY_PROBLEM);
    expect(problemMessage(new Error('boom'))).toBe(EMPTY_PROBLEM.detail);
  });

  it('merges server problem details over defaults', () => {
    const error = axiosError({
      title: 'Validation failed',
      detail: 'Check the highlighted fields',
      code: 'VALIDATION_ERROR',
      status: 400,
      requestId: 'req-1',
      errors: [
        { field: 'name', message: 'required' },
        { field: 'email', message: 'invalid' },
      ],
    });
    expect(problemFrom(error).code).toBe('VALIDATION_ERROR');
    expect(problemCode(error)).toBe('VALIDATION_ERROR');
    expect(problemMessage(error)).toBe('Check the highlighted fields');
    expect(fieldErrors(error)).toEqual({ name: 'required', email: 'invalid' });
    expect(fieldErrors(new Error('no details'))).toEqual({});
    expect(problemMessage(axiosError({ detail: null, title: 'Only a title' }))).toBe('Only a title');
  });

  it('detects conflicts and timeline conflict codes', () => {
    expect(isConflict(axiosError({ status: 409 }, 409))).toBe(true);
    expect(isConflict(axiosError({ status: 409, code: 'OTHER' }, 409), 'DRAFT_MODIFIED')).toBe(false);
    expect(isConflict(axiosError({ status: 400 }, 400))).toBe(false);
    expect(CONFLICT_CODES).toEqual(['DRAFT_MODIFIED', 'CURRENT_VERSION_CHANGED']);
    expect(isTimelineConflict(axiosError({ code: 'DRAFT_MODIFIED' }, 409))).toBe(true);
    expect(isTimelineConflict(axiosError({ code: 'CURRENT_VERSION_CHANGED' }, 409))).toBe(true);
    expect(isTimelineConflict(axiosError({ code: 'OTHER' }, 409))).toBe(false);
    expect(isTimelineConflict(new Error('boom'))).toBe(false);
  });
});
