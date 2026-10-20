import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

class FakeBroadcastChannel {
  static instances: FakeBroadcastChannel[] = [];
  onmessage: ((event: MessageEvent) => void) | null = null;
  postMessage = vi.fn();
  constructor(public name: string) {
    FakeBroadcastChannel.instances.push(this);
  }
}

describe('session store', () => {
  beforeEach(() => {
    vi.resetModules();
    FakeBroadcastChannel.instances = [];
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('publishes session updates locally and to subscribers', async () => {
    vi.stubGlobal('BroadcastChannel', undefined);
    const { sessionStore } = await import('@/auth/session');
    const listener = vi.fn();
    const unsubscribe = sessionStore.subscribe(listener);

    const user = {
      id: 'u1',
      username: 'jane',
      fullName: 'Jane',
      roles: ['USER' as const],
    };
    sessionStore.set({ accessToken: 'token-1', user });
    expect(sessionStore.get()).toEqual({ accessToken: 'token-1', user });
    expect(listener).toHaveBeenCalledTimes(1);

    sessionStore.setAccessToken('token-2');
    expect(sessionStore.get()).toEqual({ accessToken: 'token-2', user });

    sessionStore.clear();
    expect(sessionStore.get()).toEqual({ accessToken: null, user: null });
    expect(listener).toHaveBeenCalledTimes(3);

    unsubscribe();
    sessionStore.clear();
    expect(listener).toHaveBeenCalledTimes(3);
  });

  it('broadcasts updates and reacts to channel messages', async () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    const { sessionStore } = await import('@/auth/session');
    const channel = FakeBroadcastChannel.instances[0];
    expect(channel.name).toBe('prolean-auth');

    const user = {
      id: 'u2',
      username: 'bob',
      fullName: 'Bob',
      roles: ['ADMIN' as const],
    };
    sessionStore.set({ accessToken: 'token-a', user });
    expect(channel.postMessage).toHaveBeenCalledWith({
      type: 'session',
      next: { accessToken: 'token-a', user },
    });

    sessionStore.clear();
    expect(channel.postMessage).toHaveBeenCalledWith({ type: 'clear' });

    channel.onmessage?.({
      data: { type: 'session', next: { accessToken: 'remote', user } },
    } as MessageEvent);
    expect(sessionStore.get()).toEqual({ accessToken: 'remote', user });

    channel.onmessage?.({ data: { type: 'clear' } } as MessageEvent);
    expect(sessionStore.get()).toEqual({ accessToken: null, user: null });

    channel.onmessage?.({ data: { type: 'session' } } as MessageEvent);
    expect(sessionStore.get()).toEqual({ accessToken: null, user: null });
  });
});
