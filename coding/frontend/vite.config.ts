import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname,
    },
  },
  server: {
    host: true,
    port: 5173,
  },
  preview: {
    host: true,
    port: 4173,
  },
  build: {
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    testTimeout: 60_000,
    hookTimeout: 60_000,
    pool: 'threads',
    maxWorkers: 1,
    minWorkers: 1,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: [
        'src/lib/**/*.ts',
        'src/auth/**/*.{ts,tsx}',
        'src/features/**/*.ts',
        'src/components/**/*.tsx',
      ],
      exclude: [
        'src/**/*.{test,spec}.{ts,tsx}',
        'src/test/**',
        'src/types/**',
        'src/**/types.ts',
        'src/features/**/*Drawer.tsx',
        'src/vite-env.d.ts',
      ],
      thresholds: {
        global: {
          lines: 95,
          functions: 95,
          statements: 95,
          branches: 85,
        },
        'src/lib/**/*.ts': {
          lines: 100,
          functions: 100,
          statements: 100,
          branches: 90,
        },
        'src/auth/**/*.{ts,tsx}': {
          lines: 100,
          functions: 100,
          statements: 100,
          branches: 90,
        },
        'src/features/**/*.ts': {
          lines: 100,
          functions: 100,
          statements: 100,
          branches: 90,
        },
        'src/components/**/*.tsx': {
          lines: 90,
          functions: 90,
          statements: 90,
          branches: 80,
        },
      },
    },
  },
});
