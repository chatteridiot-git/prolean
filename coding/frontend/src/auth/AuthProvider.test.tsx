import { act, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/auth/refresh', () => ({ refreshSession: vi.fn() }));
vi.mock('@/api/client', () => ({
  authApi: { post: vi.fn() },
}));

import { authApi } from '@/api/client';
import { refreshSession } from '@/auth/refresh';
import { AuthProvider, useAuth } from '@/auth/AuthProvider';
import { sessionStore } from '@/auth/session';
import type { UserSummary } from '@/types/api';

const mockedRefresh = vi.mocked(refreshSession);
const mockedPost = vi.mocked(authApi.post);
const user: UserSummary = {
  id: 'u1',
  username: 'jane',
  fullName: 'Jane Doe',
  roles: ['USER'],
};
const admin: UserSummary = { ...user, id: 'u2', roles: ['ADMIN', 'USER'] };

function wrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <AuthProvider>{children}</AuthProvider>
      </QueryClientProvider>
    );
  };
}

function restoreAs(value: UserSummary) {
  mockedRefresh.mockImplementation(async () => {
    // Simulate async session setting like real refreshSession does
    await Promise.resolve();
    sessionStore.set({ accessToken: 'restored', user: value });
    return value;
  });
}

function Probe() {
  const auth = useAuth();
  return (
    <div>
      <span data-testid="status">{auth.status}</span>
      <span data-testid="name">{auth.user?.fullName ?? 'none'}</span>
      <span data-testid="admin">{String(auth.isAdmin)}</span>
      <button onClick={() => void auth.login('jane', 'Passw0rd')}>login</button>
      <button onClick={() => void auth.logout().catch(() => undefined)}>logout</button>
      <button onClick={() => void auth.refresh()}>refresh</button>
      <button onClick={auth.clearSession}>clear</button>
    </div>
  );
}

describe('AuthProvider', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.resetAllMocks();
    sessionStore.clear();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  afterEach(() => {
    sessionStore.clear();
  });

  it('restores an existing session on mount', async () => {
    restoreAs(admin);
    render(<Probe />, { wrapper: wrapper(queryClient) });
    expect(screen.getByTestId('status')).toHaveTextContent('loading');
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(screen.getByTestId('admin')).toHaveTextContent('true');
  });

  it('becomes anonymous when initial refresh fails', async () => {
    mockedRefresh.mockRejectedValue(new Error('no session'));
    render(<Probe />, { wrapper: wrapper(queryClient) });
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
  });

  it('sets status to authenticated when session has accessToken but no user', async () => {
    mockedRefresh.mockResolvedValue(user);
    // Pre-set session with accessToken only, simulating the intermediate state during refresh
    sessionStore.setAccessToken('temp-token');
    render(<Probe />, { wrapper: wrapper(queryClient) });
    // The subscription callback should set status to 'authenticated' when accessToken exists
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(sessionStore.get().user).toEqual(user);
  });

  it('logs in and stores the access token and user', async () => {
    restoreAs(user);
    mockedPost.mockResolvedValue({
      data: { accessToken: 'access-1', tokenType: 'Bearer', expiresIn: 900, user },
    } as never);
    render(<Probe />, { wrapper: wrapper(queryClient) });
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    await act(async () => {
      screen.getByText('login').click();
    });
    await waitFor(() => expect(screen.getByTestId('name')).toHaveTextContent('Jane Doe'));
    expect(mockedPost).toHaveBeenCalledWith('/api/v1/auth/login', {
      username: 'jane',
      password: 'Passw0rd',
    });
    expect(sessionStore.get().accessToken).toBe('access-1');
  });

  it('clears session and cached data on logout even when the request fails', async () => {
    restoreAs(user);
    mockedPost.mockRejectedValue(new Error('offline'));
    const clearSpy = vi.spyOn(queryClient, 'clear');
    render(<Probe />, { wrapper: wrapper(queryClient) });
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    await act(async () => {
      screen.getByText('logout').click();
    });
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
    expect(sessionStore.get()).toEqual({ accessToken: null, user: null });
    expect(clearSpy).toHaveBeenCalled();
  });

  it('manually refreshes and clears the session', async () => {
    mockedRefresh
      .mockImplementationOnce(async () => {
        await Promise.resolve();
        sessionStore.set({ accessToken: 'restored', user });
        return user;
      })
      .mockImplementationOnce(async () => {
        await Promise.resolve();
        sessionStore.set({ accessToken: 'restored-admin', user: admin });
        return admin;
      });
    render(<Probe />, { wrapper: wrapper(queryClient) });
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    await act(async () => {
      screen.getByText('refresh').click();
    });
    await waitFor(() => expect(screen.getByTestId('admin')).toHaveTextContent('true'));

    act(() => {
      screen.getByText('clear').click();
    });
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(screen.getByTestId('name')).toHaveTextContent('none');
  });

  it('reacts to an auth-expired event', async () => {
    restoreAs(user);
    render(<Probe />, { wrapper: wrapper(queryClient) });
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    act(() => {
      window.dispatchEvent(new Event('prolean:auth-expired'));
    });
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(sessionStore.get().user).toBeNull();
  });

  it('throws when useAuth is used outside a provider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Probe />)).toThrow('useAuth must be used inside AuthProvider');
    spy.mockRestore();
  });
});
