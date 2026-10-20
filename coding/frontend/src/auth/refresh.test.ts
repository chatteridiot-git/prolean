import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/raw', () => ({
  rawAuth: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

import { rawAuth } from '@/api/raw';
import { resetRefreshForTests, refreshSession } from '@/auth/refresh';
import { sessionStore } from '@/auth/session';

const post = vi.mocked(rawAuth.post);
const get = vi.mocked(rawAuth.get);
const user = { id: 'u1', username: 'jane', fullName: 'Jane Doe', roles: ['USER' as const] };
const LOCK_KEY = 'prolean.auth.refresh.lock';

function success(): void {
  post.mockResolvedValue({ data: { accessToken: 'access-1' } } as never);
  get.mockResolvedValue({ data: user } as never);
}

describe('refreshSession', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    sessionStore.clear();
    resetRefreshForTests();
    localStorage.clear();
    vi.useRealTimers();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    Reflect.deleteProperty(navigator, 'locks');
  });

  it('rotates the token and loads the current user', async () => {
    success();
    await expect(refreshSession()).resolves.toEqual(user);
    expect(post).toHaveBeenCalledWith('/api/v1/auth/refresh');
    expect(get).toHaveBeenCalledWith('/api/v1/auth/me', {
      headers: { Authorization: 'Bearer access-1' },
    });
    expect(sessionStore.get()).toEqual({ accessToken: 'access-1', user });
  });

  it('clears the session when the refresh fails', async () => {
    post.mockRejectedValue(new Error('expired'));
    await expect(refreshSession()).rejects.toThrow('expired');
    expect(sessionStore.get()).toEqual({ accessToken: null, user: null });
  });

  it('shares a single refresh request between concurrent callers', async () => {
    let resolvePost!: (value: unknown) => void;
    post.mockReturnValue(new Promise((resolve) => (resolvePost = resolve)) as never);
    get.mockResolvedValue({ data: user } as never);

    const first = refreshSession();
    const second = refreshSession();
    expect(post).toHaveBeenCalledTimes(1);
    resolvePost({ data: { accessToken: 'shared' } });
    await expect(first).resolves.toEqual(user);
    await expect(second).resolves.toEqual(user);
  });

  it('uses the Web Locks API when available', async () => {
    const request = vi.fn((_name: string, work: () => Promise<unknown>) => work());
    Object.defineProperty(navigator, 'locks', { configurable: true, value: { request } });
    success();

    await refreshSession();
    expect(request).toHaveBeenCalledWith('prolean-auth-refresh', expect.any(Function));
  });

  it('skips local storage locking when storage is unavailable', async () => {
    vi.stubGlobal('localStorage', undefined);
    success();
    await expect(refreshSession()).resolves.toEqual(user);
  });

  it('replaces an expired fallback lock', async () => {
    localStorage.setItem(
      LOCK_KEY,
      JSON.stringify({ owner: 'other-tab', expiresAt: Date.now() - 1 }),
    );
    success();
    await refreshSession();
    expect(localStorage.getItem(LOCK_KEY)).toBeNull();
  });

  it('waits for a held lock and does not remove another owner lock', async () => {
    vi.useFakeTimers();
    localStorage.setItem(
      LOCK_KEY,
      JSON.stringify({ owner: 'other-tab', expiresAt: Date.now() + 60_000 }),
    );
    success();

    const pending = refreshSession();
    await vi.advanceTimersByTimeAsync(10_100);
    await expect(pending).resolves.toEqual(user);
    expect(JSON.parse(localStorage.getItem(LOCK_KEY) ?? '{}').owner).toBe('other-tab');
  });

  it('leaves a lock owned by another tab in place', async () => {
    post.mockImplementation(async () => {
      localStorage.setItem(
        LOCK_KEY,
        JSON.stringify({ owner: 'another-tab', expiresAt: Date.now() + 60_000 }),
      );
      return { data: { accessToken: 'x' } } as never;
    });
    get.mockResolvedValue({ data: user } as never);

    await refreshSession();
    expect(JSON.parse(localStorage.getItem(LOCK_KEY) ?? '{}').owner).toBe('another-tab');
  });

  it('uses navigator.locks when available', async () => {
    const request = vi.fn((_name: string, work: () => Promise<unknown>) => work());
    Object.defineProperty(navigator, 'locks', { configurable: true, value: { request } });
    success();

    await refreshSession();
    expect(request).toHaveBeenCalledWith('prolean-auth-refresh', expect.any(Function));
  });

  it('removes lock in finally when owner matches', async () => {
    success();
    await refreshSession();
    expect(localStorage.getItem(LOCK_KEY)).toBeNull();
  });

  it('does not remove lock in finally when owner differs', async () => {
    post.mockImplementation(async () => {
      // During work, another tab takes the lock
      localStorage.setItem(
        LOCK_KEY,
        JSON.stringify({ owner: 'other-tab', expiresAt: Date.now() + 60_000 }),
      );
      return { data: { accessToken: 'x' } } as never;
    });
    get.mockResolvedValue({ data: user } as never);

    await refreshSession();
    expect(JSON.parse(localStorage.getItem(LOCK_KEY) ?? '{}').owner).toBe('other-tab');
  });
});
