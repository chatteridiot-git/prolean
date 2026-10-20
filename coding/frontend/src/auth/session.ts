import type { UserSummary } from '@/types/api';

export interface Session {
  accessToken: string | null;
  user: UserSummary | null;
}

let session: Session = { accessToken: null, user: null };
const listeners = new Set<(value: Session) => void>();
const channel =
  typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('prolean-auth') : null;

function publish(next: Session) {
  session = next;
  listeners.forEach((listener) => listener(session));
}

export const sessionStore = {
  get(): Session {
    return session;
  },
  set(next: Session): void {
    publish(next);
    channel?.postMessage({ type: 'session', next });
  },
  setAccessToken(accessToken: string | null): void {
    publish({ ...session, accessToken });
  },
  clear(): void {
    publish({ accessToken: null, user: null });
    channel?.postMessage({ type: 'clear' });
  },
  subscribe(listener: (value: Session) => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

if (channel) {
  channel.onmessage = (event: MessageEvent<{ type: string; next?: Session }>) => {
    if (event.data.type === 'session' && event.data.next) {
      publish(event.data.next);
    } else if (event.data.type === 'clear') {
      publish({ accessToken: null, user: null });
    }
  };
}
