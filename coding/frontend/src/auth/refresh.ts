import { rawAuth } from '@/api/raw';
import { sessionStore } from '@/auth/session';
import type { RefreshResponse, UserSummary } from '@/types/api';

let refreshPromise: Promise<UserSummary> | null = null;
const LOCK_KEY = 'prolean.auth.refresh.lock';
const LOCK_LEASE_MS = 10_000;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function withLocalStorageLock<T>(work: () => Promise<T>): Promise<T> {
  if (typeof localStorage === 'undefined') return work();
  const owner = crypto.randomUUID();
  const deadline = Date.now() + LOCK_LEASE_MS;
  for (;;) {
    const raw = localStorage.getItem(LOCK_KEY);
    const parsed = raw ? (JSON.parse(raw) as { owner: string; expiresAt: number }) : null;
    if (!parsed || parsed.expiresAt < Date.now()) {
      localStorage.setItem(LOCK_KEY, JSON.stringify({ owner, expiresAt: Date.now() + LOCK_LEASE_MS }));
      break;
    }
    if (Date.now() > deadline) break;
    await sleep(50);
  }
  try {
    return await work();
  } finally {
    const raw = localStorage.getItem(LOCK_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as { owner?: string };
      if (parsed.owner === owner) localStorage.removeItem(LOCK_KEY);
    }
  }
}

async function withRefreshLock<T>(work: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks;
  if (locks?.request) {
    return locks.request('prolean-auth-refresh', work);
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
