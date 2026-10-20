/// <reference types="vite/client" />

interface ProLeanRuntimeConfig {
  authApiUrl?: string;
  userApiUrl?: string;
  taskApiUrl?: string;
  timelineApiUrl?: string;
}

interface Window {
  __PROLEAN_CONFIG__?: ProLeanRuntimeConfig;
}

declare module '*.css';
