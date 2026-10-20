import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/auth/AuthProvider', () => ({ useAuth: vi.fn() }));

import { useAuth } from '@/auth/AuthProvider';
import { RequireAdmin, RequireAuth } from '@/app/guards';
import { ForbiddenPage, NotFoundPage } from '@/pages/ErrorPages';
import { LoginPage } from '@/pages/LoginPage';
import type { AuthContextValue } from '@/auth/AuthProvider';

const mockedUseAuth = vi.mocked(useAuth);

function auth(overrides: Partial<AuthContextValue> = {}): AuthContextValue {
  return {
    status: 'anonymous',
    user: null,
    accessToken: null,
    isAdmin: false,
    login: vi.fn(),
    logout: vi.fn(),
    refresh: vi.fn(),
    clearSession: vi.fn(),
    ...overrides,
  };
}

function renderLogin(authValue: AuthContextValue) {
  mockedUseAuth.mockReturnValue(authValue);
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>Dashboard</div>} />
        <Route path="/projects" element={<div>Projects</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('validates required credentials before calling the API', async () => {
    const user = userEvent.setup();
    const login = vi.fn();
    renderLogin(auth({ login }));
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Username is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('logs in, trims the username, and navigates to the requested page', async () => {
    const user = userEvent.setup();
    const login = vi.fn().mockResolvedValue({});
    mockedUseAuth.mockReturnValue(auth({ login }));
    render(
      <MemoryRouter
        initialEntries={[{ pathname: '/login', state: { from: '/projects' } }]}
      >
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/projects" element={<div>Projects</div>} />
        </Routes>
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText('Username'), '  jane  ');
    await user.type(screen.getByLabelText('Password'), 'Passw0rd');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('jane', 'Passw0rd'));
    expect(await screen.findByText('Projects')).toBeInTheDocument();
  });

  it('shows Problem Details when login fails', async () => {
    const user = userEvent.setup();
    const login = vi.fn().mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 401,
        data: { detail: 'Username or password is incorrect.', requestId: 'req-1' },
      },
    });
    renderLogin(auth({ login }));

    await user.type(screen.getByLabelText('Username'), 'jane');
    await user.type(screen.getByLabelText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Username or password is incorrect.')).toBeInTheDocument();
  });

  it('redirects an authenticated user to the dashboard', async () => {
    renderLogin(auth({ status: 'authenticated' }));
    expect(await screen.findByText('Dashboard')).toBeInTheDocument();
  });
});

describe('route guards', () => {
  beforeEach(() => vi.resetAllMocks());

  it('shows a restoring state while auth is loading', () => {
    mockedUseAuth.mockReturnValue(auth({ status: 'loading' }));
    render(
      <MemoryRouter>
        <RequireAuth>
          <div>Private</div>
        </RequireAuth>
      </MemoryRouter>,
    );
    expect(document.querySelector('.ant-spin')).toBeInTheDocument();
    expect(screen.queryByText('Private')).not.toBeInTheDocument();
  });

  it('redirects anonymous users to login and preserves the path', async () => {
    mockedUseAuth.mockReturnValue(auth({ status: 'anonymous' }));
    render(
      <MemoryRouter initialEntries={['/projects']}>
        <Routes>
          <Route path="/login" element={<div>Login screen</div>} />
          <Route
            path="/projects"
            element={
              <RequireAuth>
                <div>Private</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('Login screen')).toBeInTheDocument();
  });

  it('renders protected content for authenticated users and outlet routes', () => {
    mockedUseAuth.mockReturnValue(auth({ status: 'authenticated' }));
    const protectedView = render(
      <MemoryRouter>
        <RequireAuth>
          <div>Private</div>
        </RequireAuth>
      </MemoryRouter>,
    );
    expect(screen.getByText('Private')).toBeInTheDocument();
    protectedView.unmount();

    render(
      <MemoryRouter initialEntries={['/projects']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/projects" element={<div>Outlet content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('Outlet content')).toBeInTheDocument();
  });

  it('allows admins and rejects members from admin routes', () => {
    mockedUseAuth.mockReturnValue(auth({ isAdmin: true }));
    const adminView = render(
      <MemoryRouter>
        <RequireAdmin>
          <div>Admin content</div>
        </RequireAdmin>
      </MemoryRouter>,
    );
    expect(screen.getByText('Admin content')).toBeInTheDocument();
    adminView.unmount();

    mockedUseAuth.mockReturnValue(auth({ isAdmin: false }));
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/forbidden" element={<div>Forbidden</div>} />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <div>Admin content</div>
              </RequireAdmin>
            }
          />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('Forbidden')).toBeInTheDocument();
  });
});

describe('error pages', () => {
  it('navigates back to the dashboard', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/missing']}>
        <Routes>
          <Route path="/" element={<div>Dashboard</div>} />
          <Route path="/missing" element={<NotFoundPage />} />
          <Route path="/forbidden" element={<ForbiddenPage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('404')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Back to dashboard' }));
    expect(await screen.findByText('Dashboard')).toBeInTheDocument();
  });
});
