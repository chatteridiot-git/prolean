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
    vi.stubGlobal('BroadcastChannel', undefined);
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
    await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
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

  it('fails closed and leaves another owner lock in place when the lease never expires', async () => {
    vi.useFakeTimers();
    localStorage.setItem(
      LOCK_KEY,
      JSON.stringify({ owner: 'other-tab', expiresAt: Date.now() + 60_000 }),
    );
    success();

    const pending = refreshSession();
    const rejection = expect(pending).rejects.toThrow('Timed out waiting for the cross-tab refresh lock');
    await vi.advanceTimersByTimeAsync(10_100);
    await rejection;
    expect(post).not.toHaveBeenCalled();
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

  it('uses the BroadcastChannel fallback when Web Locks is unavailable', async () => {
    const messages: Array<{ type: string }> = [];
    let reannounced = false;
    class FakeChannel {
      onmessage: ((event: MessageEvent) => void) | null = null;
      postMessage = vi.fn((message: { type: string; id: string }) => {
        messages.push(message);
        if (message.type === 'probe') {
          this.onmessage?.({ data: undefined } as MessageEvent);
        } else if (message.type === 'start' && !reannounced) {
          reannounced = true;
          this.onmessage?.({ data: { type: 'probe', id: 'new-tab' } } as MessageEvent);
          this.onmessage?.({ data: { type: 'probe', id: message.id } } as MessageEvent);
        }
      });
      close = vi.fn();
    }
    vi.stubGlobal('BroadcastChannel', FakeChannel);
    success();

    await expect(refreshSession()).resolves.toEqual(user);
    expect(messages.map((message) => message.type)).toEqual(['probe', 'start', 'start', 'done']);
  });

  it('waits for a busy peer and falls through when it left no session', async () => {
    class PeerChannel {
      onmessage: ((event: MessageEvent) => void) | null = null;
      postMessage = vi.fn((message: { type: string }) => {
        if (message.type !== 'probe') return;
        this.onmessage?.({ data: { type: 'start', id: 'peer' } } as MessageEvent);
        setTimeout(
          () => this.onmessage?.({ data: { type: 'done', id: 'peer' } } as MessageEvent),
          120,
        );
      });
      close = vi.fn();
    }
    vi.stubGlobal('BroadcastChannel', PeerChannel);
    success();

    await expect(refreshSession()).resolves.toEqual(user);
    expect(post).toHaveBeenCalledTimes(1);
  });

  it('waits for a peer start/done pair and then refreshes', async () => {
    const messages: Array<{ type: string }> = [];
    class PeerChannel {
      onmessage: ((event: MessageEvent) => void) | null = null;
      postMessage = vi.fn((message: { type: string }) => {
        messages.push(message);
        if (message.type !== 'probe') return;
        this.onmessage?.({ data: { type: 'probe', id: 'early-tab' } } as MessageEvent);
        this.onmessage?.({ data: { type: 'start', id: 'peer' } } as MessageEvent);
        this.onmessage?.({ data: { type: 'busy', id: 'other-waiter' } } as MessageEvent);
        this.onmessage?.({ data: { type: 'probe', id: 'late-tab' } } as MessageEvent);
        setTimeout(
          () => this.onmessage?.({ data: { type: 'done', id: 'peer' } } as MessageEvent),
          100,
        );
      });
      close = vi.fn();
    }
    vi.stubGlobal('BroadcastChannel', PeerChannel);
    success();

    await expect(refreshSession()).resolves.toEqual(user);
    expect(post).toHaveBeenCalledTimes(1);
    expect(messages.filter((message) => message.type === 'start')).toHaveLength(1);
  });

  it('never replies to a busy message', async () => {
    const messages: Array<{ type: string }> = [];
    class BusyEchoChannel {
      onmessage: ((event: MessageEvent) => void) | null = null;
      postMessage = vi.fn((message: { type: string }) => {
        messages.push(message);
        if (message.type === 'probe') {
          this.onmessage?.({ data: { type: 'busy', id: 'peer' } } as MessageEvent);
          setTimeout(
            () => this.onmessage?.({ data: { type: 'done', id: 'peer' } } as MessageEvent),
            10,
          );
        }
      });
      close = vi.fn();
    }
    vi.stubGlobal('BroadcastChannel', BusyEchoChannel);
    success();

    await expect(refreshSession()).resolves.toEqual(user);
    expect(messages.filter((message) => message.type === 'busy')).toHaveLength(0);
    expect(post).toHaveBeenCalledTimes(1);
  });

  it('reuses the session broadcast by a peer that holds the fallback lock', async () => {
    class PeerChannel {
      onmessage: ((event: MessageEvent) => void) | null = null;
      postMessage = vi.fn((message: { type: string }) => {
        if (message.type !== 'probe') return;
        this.onmessage?.({ data: { type: 'start', id: 'peer' } } as MessageEvent);
        setTimeout(() => this.onmessage?.({ data: { type: 'done', id: 'peer' } } as MessageEvent), 120);
      });
      close = vi.fn();
    }
    vi.stubGlobal('BroadcastChannel', PeerChannel);
    sessionStore.set({ accessToken: 'already-refreshed', user });

    await expect(refreshSession()).resolves.toEqual(user);
    expect(post).not.toHaveBeenCalled();
    expect(get).not.toHaveBeenCalled();
  });

  it('treats a corrupt lease as absent', async () => {
    localStorage.setItem(LOCK_KEY, 'not-json');
    success();
    await expect(refreshSession()).resolves.toEqual(user);
    expect(localStorage.getItem(LOCK_KEY)).toBeNull();
  });

  it('treats a malformed lease as absent', async () => {
    localStorage.setItem(LOCK_KEY, JSON.stringify({ owner: 7, expiresAt: 'later' }));
    success();
    await expect(refreshSession()).resolves.toEqual(user);
    expect(localStorage.getItem(LOCK_KEY)).toBeNull();
  });

  it('fails closed when a peer never finishes refreshing', async () => {
    vi.useFakeTimers();
    class StuckPeer {
      onmessage: ((event: MessageEvent) => void) | null = null;
      postMessage = vi.fn((message: { type: string }) => {
        if (message.type === 'probe') {
          this.onmessage?.({ data: { type: 'start', id: 'stuck' } } as MessageEvent);
        }
      });
      close = vi.fn();
    }
    vi.stubGlobal('BroadcastChannel', StuckPeer);

    const pending = refreshSession();
    const rejection = expect(pending).rejects.toThrow(
      'Timed out waiting for another tab to finish refreshing',
    );
    await vi.advanceTimersByTimeAsync(5_100);
    await rejection;
    expect(post).not.toHaveBeenCalled();
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
