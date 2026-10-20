import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import { BrowserRouter } from 'react-router-dom';
import 'antd/dist/reset.css';
import './styles.css';
import { App } from '@/app/App';
import { AuthProvider } from '@/auth/AuthProvider';
import { problemFrom } from '@/lib/errors';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        const status = problemFrom(error).status;
        if (status && status >= 400 && status < 500) return false;
        return failureCount < 1;
      },
      staleTime: 20_000,
      refetchOnWindowFocus: false,
    },
    mutations: { retry: 0 },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ConfigProvider
        theme={{
          token: {
            colorPrimary: '#0f766e',
            borderRadius: 8,
            fontFamily:
              "'Inter', 'Segoe UI', system-ui, -apple-system, 'Helvetica Neue', Arial, sans-serif",
          },
          components: {
            Layout: { headerBg: '#ffffff', siderBg: '#0f3d3a' },
            Menu: {
              darkItemBg: '#0f3d3a',
              darkItemSelectedBg: '#115e59',
              darkItemColor: 'rgba(255,255,255,0.75)',
              darkItemSelectedColor: '#ffffff',
            },
          },
        }}
      >
        <AntApp>
          <BrowserRouter>
            <AuthProvider>
              <App />
            </AuthProvider>
          </BrowserRouter>
        </AntApp>
      </ConfigProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
