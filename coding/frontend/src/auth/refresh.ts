import { rawAuth } from '@/api/raw';
import { sessionStore } from '@/auth/session';
import type { RefreshResponse, UserSummary } from '@/types/api';

let refreshPromise: Promise<UserSummary> | null = null;
const LOCK_KEY = 'prolean.auth.refresh.lock';
const LOCK_LEASE_MS = 10_000;
const CHANNEL_NAME = 'prolean-auth-refresh-lock';
const PEER_WAIT_MS = 5_000;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

interface RefreshLease {
  owner: string;
  expiresAt: number;
}

function readLease(raw: string | null): RefreshLease | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<RefreshLease> | null;
    if (!parsed || typeof parsed.owner !== 'string' || typeof parsed.expiresAt !== 'number') {
      return null;
    }
    return { owner: parsed.owner, expiresAt: parsed.expiresAt };
  } catch {
    return null;
  }
}

async function withLocalStorageLock<T>(work: () => Promise<T>): Promise<T> {
  if (typeof localStorage === 'undefined') return work();
  const owner = crypto.randomUUID();
  const deadline = Date.now() + LOCK_LEASE_MS;
  for (;;) {
    const parsed = readLease(localStorage.getItem(LOCK_KEY));
    if (!parsed || parsed.expiresAt < Date.now()) {
      localStorage.setItem(LOCK_KEY, JSON.stringify({ owner, expiresAt: Date.now() + LOCK_LEASE_MS }));
      // A concurrent writer can overwrite us; give it a tick, then never run the refresh unless we
      // still own the lease.
      await sleep(5);
      if (readLease(localStorage.getItem(LOCK_KEY))?.owner === owner) break;
    }
    if (Date.now() > deadline) {
      throw new Error('Timed out waiting for the cross-tab refresh lock');
    }
    await sleep(50);
  }
  try {
    return await work();
  } finally {
    if (readLease(localStorage.getItem(LOCK_KEY))?.owner === owner) {
      localStorage.removeItem(LOCK_KEY);
    }
  }
}

/**
 * Fallback for browsers without Web Locks. Tabs announce their refresh over a BroadcastChannel and
 * wait for an in-flight peer; the localStorage lease still provides the final serialization guard.
 */
async function withBroadcastChannelLock<T>(work: () => Promise<T>): Promise<T> {
  const channel = new BroadcastChannel(CHANNEL_NAME);
  const id = crypto.randomUUID();
  const peers = new Set<string>();
  let peerStarted = false;
  let refreshing = false;
  let releasePeer: (() => void) | null = null;

  channel.onmessage = (event: MessageEvent<{ type?: string; id?: string }>) => {
    const message = event.data;
    if (!message?.id || message.id === id) return;
    if (message.type === 'probe') {
      // Re-announce an in-flight refresh so a tab that just opened its channel learns about it.
      // A waiting tab must not announce itself: the actual refresher answers probes.
      if (refreshing) channel.postMessage({ type: 'start', id });
    } else if (message.type === 'start') {
      // Only tabs that actually started a refresh announce 'start'; waiters' 'busy' replies are
      // ignored so two waiters can never wait on each other.
      peerStarted = true;
      peers.add(message.id);
    } else if (message.type === 'done') {
      peers.delete(message.id);
      if (peers.size === 0) releasePeer?.();
    }
  };

  channel.postMessage({ type: 'probe', id });
  await sleep(50);
  if (peers.size > 0) {
    await new Promise<void>((resolve) => {
      const timer = setTimeout(() => {
        releasePeer = null;
        resolve();
      }, PEER_WAIT_MS);
      releasePeer = () => {
        clearTimeout(timer);
        releasePeer = null;
        resolve();
      };
    });
  }
  if (peers.size > 0) {
    channel.close();
    throw new Error('Timed out waiting for another tab to finish refreshing');
  }
  if (peerStarted) {
    const current = sessionStore.get();
    if (current.user) {
      channel.close();
      return current.user as T;
    }
  }

  refreshing = true;
  channel.postMessage({ type: 'start', id });
  try {
    return await withLocalStorageLock(work);
  } finally {
    refreshing = false;
    channel.postMessage({ type: 'done', id });
    channel.close();
  }
}

async function withRefreshLock<T>(work: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks;
  if (locks?.request) {
    return locks.request('prolean-auth-refresh', work);
  }
  if (typeof BroadcastChannel !== 'undefined') {
    return withBroadcastChannelLock(work);
  }
  return withLocalStorageLock(work);
}

async function performRefresh(): Promise<UserSummary> {
  try {
    const refresh = await rawAuth.post<RefreshResponse>('/api/v1/auth/refresh');
    sessionStore.setAccessToken(refresh.data.accessToken);
    const me = await rawAuth.get<UserSummary>('/api/v1/auth/me', {
      headers: { Authorization: `Bearer ${refresh.data.accessToken}` },
    });
    sessionStore.set({ accessToken: refresh.data.accessToken, user: me.data });
    return me.data;
  } catch (error) {
    sessionStore.clear();
    throw error;
  }
}

/** Refreshes once, sharing the in-flight request between callers and browser tabs. */
export function refreshSession(): Promise<UserSummary> {
  if (!refreshPromise) {
    refreshPromise = withRefreshLock(performRefresh).finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

export function resetRefreshForTests(): void {
  refreshPromise = null;
}
