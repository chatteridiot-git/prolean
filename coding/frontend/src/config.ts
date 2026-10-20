export interface RuntimeConfig {
  authApiUrl: string;
  userApiUrl: string;
  taskApiUrl: string;
  timelineApiUrl: string;
}

const defaults: RuntimeConfig = {
  authApiUrl: import.meta.env.VITE_AUTH_API_URL || 'http://localhost:8081',
  userApiUrl: import.meta.env.VITE_USER_API_URL || 'http://localhost:8082',
  taskApiUrl: import.meta.env.VITE_TASK_API_URL || 'http://localhost:8083',
  timelineApiUrl: import.meta.env.VITE_TIMELINE_API_URL || 'http://localhost:8084',
};

export const runtimeConfig: RuntimeConfig = {
  ...defaults,
  ...(typeof window === 'undefined' ? {} : window.__PROLEAN_CONFIG__),
};

export const isAdmin = (roles: readonly string[] | undefined): boolean =>
  roles?.includes('ADMIN') ?? false;
