import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi } from '@/api/client';
import { refreshSession } from '@/auth/refresh';
import { sessionStore } from '@/auth/session';
import { isAdmin } from '@/config';
import type { LoginResponse, UserSummary } from '@/types/api';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  status: AuthStatus;
  user: UserSummary | null;
  accessToken: string | null;
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<UserSummary>;
  logout: () => Promise<void>;
  refresh: () => Promise<UserSummary>;
  clearSession: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<UserSummary | null>(sessionStore.get().user);
  const [accessToken, setAccessToken] = useState<string | null>(sessionStore.get().accessToken);

  useEffect(() => {
    const unsubscribe = sessionStore.subscribe((session) => {
      setUser(session.user);
      setAccessToken(session.accessToken);
      setStatus((current) => {
        if (session.user) return 'authenticated';
        if (session.accessToken) return current === 'loading' ? 'loading' : 'authenticated';
        return current === 'loading' ? 'loading' : 'anonymous';
      });
    });
    const expired = () => {
      sessionStore.clear();
      queryClient.clear();
      setStatus('anonymous');
    };
    window.addEventListener('prolean:auth-expired', expired);
    refreshSession()
      .then(() => setStatus('authenticated'))
      .catch(() => setStatus('anonymous'));
    return () => {
      unsubscribe();
      window.removeEventListener('prolean:auth-expired', expired);
    };
  }, [queryClient]);

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.post<LoginResponse>('/api/v1/auth/login', { username, password });
    sessionStore.set({ accessToken: response.data.accessToken, user: response.data.user });
    setStatus('authenticated');
    return response.data.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.post('/api/v1/auth/logout');
    } finally {
      sessionStore.clear();
      queryClient.clear();
      setStatus('anonymous');
    }
  }, [queryClient]);

  const refresh = useCallback(async () => {
    const refreshed = await refreshSession();
    setStatus('authenticated');
    return refreshed;
  }, []);

  const clearSession = useCallback(() => {
    sessionStore.clear();
    queryClient.clear();
    setStatus('anonymous');
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      accessToken,
      isAdmin: isAdmin(user?.roles),
      login,
      logout,
      refresh,
      clearSession,
    }),
    [status, user, accessToken, login, logout, refresh, clearSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
