import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/auth/refresh', () => ({ refreshSession: vi.fn() }));

import { authApi } from '@/api/client';
import { refreshSession } from '@/auth/refresh';
import { sessionStore } from '@/auth/session';

const mockedRefresh = vi.mocked(refreshSession);

function unauthorized(config: InternalAxiosRequestConfig): AxiosError {
  return new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
    data: { code: 'UNAUTHORIZED' },
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
  });
}

function ok(config: InternalAxiosRequestConfig, data: unknown = { ok: true }): AxiosResponse {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  };
}

describe('API client interceptors', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    sessionStore.clear();
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('adds request IDs and bearer tokens to requests', async () => {
    sessionStore.set({
      accessToken: 'access-1',
      user: { id: 'u1', username: 'jane', fullName: 'Jane', roles: ['USER'] },
    });
    let captured: InternalAxiosRequestConfig | undefined;
    authApi.defaults.adapter = async (config) => {
      captured = config;
      return ok(config);
    };

    await authApi.get('/api/v1/auth/me');
    expect(captured?.headers.get('X-Request-Id')).toMatch(/^[0-9a-f-]{36}$/);
    expect(captured?.headers.get('Authorization')).toBe('Bearer access-1');
  });

  it('omits Authorization when there is no token and falls back without randomUUID', async () => {
    vi.stubGlobal('crypto', {});
    let captured: InternalAxiosRequestConfig | undefined;
    authApi.defaults.adapter = async (config) => {
      captured = config;
      return ok(config);
    };
    await authApi.get('/api/v1/auth/me');
    expect(captured?.headers.has('Authorization')).toBe(false);
    expect(captured?.headers.get('X-Request-Id')).toMatch(/^req-/);
  });

  it('refreshes once and retries a 401 request', async () => {
    let calls = 0;
    authApi.defaults.adapter = async (config) => {
      calls += 1;
      if (calls === 1) throw unauthorized(config);
      return ok(config, { retried: true });
    };
    mockedRefresh.mockImplementation(async () => {
      sessionStore.setAccessToken('refreshed-token');
      return { id: 'u1', username: 'jane', fullName: 'Jane', roles: ['USER'] };
    });

    const response = await authApi.get('/api/v1/projects');
    expect(response.data).toEqual({ retried: true });
    expect(mockedRefresh).toHaveBeenCalledTimes(1);
    expect(calls).toBe(2);
  });

  it('does not retry a repeated 401 after one refresh attempt', async () => {
    authApi.defaults.adapter = async (config) => {
      throw unauthorized(config);
    };
    mockedRefresh.mockImplementation(async () => {
      sessionStore.setAccessToken('refreshed-token');
      return { id: 'u1', username: 'jane', fullName: 'Jane', roles: ['USER'] };
    });

    await expect(authApi.get('/api/v1/projects')).rejects.toBeInstanceOf(AxiosError);
    expect(mockedRefresh).toHaveBeenCalledTimes(1);
  });

  it('does not attempt refresh for login and refresh endpoints', async () => {
    authApi.defaults.adapter = async (config) => {
      throw unauthorized(config);
    };

    await expect(authApi.post('/api/v1/auth/login')).rejects.toBeInstanceOf(AxiosError);
    await expect(authApi.post('/api/v1/auth/refresh')).rejects.toBeInstanceOf(AxiosError);
    expect(mockedRefresh).not.toHaveBeenCalled();
  });

  it('clears the session and emits an expiry event when refresh fails', async () => {
    authApi.defaults.adapter = async (config) => {
      throw unauthorized(config);
    };
    mockedRefresh.mockRejectedValue(new Error('refresh failed'));
    const expired = vi.fn();
    window.addEventListener('prolean:auth-expired', expired);
    sessionStore.set({
      accessToken: 'old',
      user: { id: 'u1', username: 'jane', fullName: 'Jane', roles: ['USER'] },
    });

    await expect(authApi.get('/api/v1/projects')).rejects.toThrow('refresh failed');
    expect(sessionStore.get()).toEqual({ accessToken: null, user: null });
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener('prolean:auth-expired', expired);
  });

  it('passes non-401 failures through without refreshing', async () => {
    authApi.defaults.adapter = async (config) => {
      throw new AxiosError('Server error', 'ERR_BAD_RESPONSE', config, undefined, {
        data: {},
        status: 500,
        statusText: 'Server Error',
        headers: {},
        config,
      });
    };
    await expect(authApi.get('/api/v1/projects')).rejects.toThrow('Server error');
    expect(mockedRefresh).not.toHaveBeenCalled();
  });
});
