import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useIdempotencyKey } from '@/lib/requestId';

describe('useIdempotencyKey', () => {
  afterEach(() => vi.restoreAllMocks());

  it('reuses one key across retries and resets after success', () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111');
    const { result } = renderHook(() => useIdempotencyKey());

    const first = result.current.get();
    const retry = result.current.get();
    expect(first).toBe(retry);

    act(() => result.current.reset());
    expect(result.current.get()).toBe('11111111-1111-4111-8111-111111111111');
  });

  it('generates a new key for each render tree', () => {
    let value = 0;
    vi.spyOn(crypto, 'randomUUID').mockImplementation(() => `key-${++value}` as ReturnType<typeof crypto.randomUUID>);
    const first = renderHook(() => useIdempotencyKey());
    const second = renderHook(() => useIdempotencyKey());
    expect(first.result.current.get()).toBe('key-1');
    expect(second.result.current.get()).toBe('key-2');
  });
});
