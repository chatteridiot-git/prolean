import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import { refreshSession } from '@/auth/refresh';
import { sessionStore } from '@/auth/session';
import { rawAuth, rawTask, rawTimeline, rawUser } from '@/api/raw';

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

function requestId(): string {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function isRefreshExcluded(url: string | undefined): boolean {
  return Boolean(url?.includes('/api/v1/auth/login') || url?.includes('/api/v1/auth/refresh'));
}

function attach(instance: AxiosInstance): AxiosInstance {
  instance.interceptors.request.use((config) => {
    config.headers.set('X-Request-Id', requestId());
    const token = sessionStore.get().accessToken;
    if (token) config.headers.set('Authorization', `Bearer ${token}`);
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const config = error.config as RetryConfig | undefined;
      if (error.response?.status !== 401 || !config || config._retry || isRefreshExcluded(config.url)) {
        return Promise.reject(error);
      }
      config._retry = true;
      try {
        await refreshSession();
        const token = sessionStore.get().accessToken;
        if (token) config.headers.set('Authorization', `Bearer ${token}`);
        return instance.request(config);
      } catch (refreshError) {
        sessionStore.clear();
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new Event('prolean:auth-expired'));
        }
        return Promise.reject(refreshError);
      }
    },
  );
  return instance;
}

export const authApi = attach(rawAuth);
export const userApi = attach(rawUser);
export const taskApi = attach(rawTask);
export const timelineApi = attach(rawTimeline);
