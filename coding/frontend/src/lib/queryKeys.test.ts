import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { queryKeys } from '@/lib/queryKeys';
import { useDebouncedValue } from '@/lib/useDebouncedValue';

describe('query keys', () => {
  it('builds stable hierarchical keys', () => {
    expect(queryKeys.auth.me).toEqual(['auth', 'me']);
    expect(queryKeys.projects.all).toEqual(['projects']);
    expect(queryKeys.projects.list({ page: 0 })).toEqual(['projects', 'list', { page: 0 }]);
    expect(queryKeys.projects.detail('p1')).toEqual(['projects', 'detail', 'p1']);
    expect(queryKeys.projects.members('p1')).toEqual(['projects', 'p1', 'members', 0]);
    expect(queryKeys.projects.members('p1', 2)).toEqual(['projects', 'p1', 'members', 2]);
    expect(queryKeys.tasks.list('p1', { page: 1 })).toEqual([
      'projects',
      'p1',
      'tasks',
      { page: 1 },
    ]);
    expect(queryKeys.tasks.detail('p1', 't1')).toEqual(['projects', 'p1', 'tasks', 't1']);
    expect(queryKeys.timeline.aggregate('p1')).toEqual(['projects', 'p1', 'timeline']);
    expect(queryKeys.timeline.versions('p1', 0)).toEqual([
      'projects',
      'p1',
      'timeline',
      'versions',
      0,
    ]);
    expect(queryKeys.users.list({ q: 'a' })).toEqual(['users', 'list', { q: 'a' }]);
    expect(queryKeys.users.roles).toEqual(['users', 'roles']);
    expect(queryKeys.holidays.list(2026, 1)).toEqual(['holidays', 2026, 1]);
    expect(queryKeys.dashboard.all).toEqual(['dashboard']);
  });
});

describe('useDebouncedValue', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('updates after the delay and debounces rapid changes', () => {
    const { result, rerender, unmount } = renderHook(
      ({ value }) => useDebouncedValue(value, 100),
      { initialProps: { value: 'a' } },
    );
    expect(result.current).toBe('a');
    rerender({ value: 'b' });
    expect(result.current).toBe('a');
    act(() => {
      vi.advanceTimersByTime(50);
    });
    rerender({ value: 'c' });
    act(() => {
      vi.advanceTimersByTime(99);
    });
    expect(result.current).toBe('a');
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toBe('c');
    unmount();
  });
});
