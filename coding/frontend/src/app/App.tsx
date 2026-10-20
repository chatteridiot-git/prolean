import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAdmin, RequireAuth } from '@/app/guards';
import { AppShell } from '@/app/AppShell';
import { ProjectLayout } from '@/app/ProjectLayout';
import { DashboardPage } from '@/pages/DashboardPage';
import { ForbiddenPage, NotFoundPage } from '@/pages/ErrorPages';
import { HolidaysPage } from '@/pages/HolidaysPage';
import { LoginPage } from '@/pages/LoginPage';
import { MembersPage } from '@/pages/MembersPage';
import { ProfilePage } from '@/pages/ProfilePage';
import { ProjectsPage } from '@/pages/ProjectsPage';
import { TasksPage } from '@/pages/TasksPage';
import { TimelinePage } from '@/pages/TimelinePage';
import { UsersPage } from '@/pages/UsersPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="projects" element={<ProjectsPage />} />
          <Route path="projects/:projectId" element={<ProjectLayout />}>
            <Route index element={<Navigate to="tasks" replace />} />
            <Route path="tasks" element={<TasksPage />} />
            <Route path="timeline" element={<TimelinePage />} />
            <Route path="members" element={<MembersPage />} />
          </Route>
          <Route
            path="users"
            element={
              <RequireAdmin>
                <UsersPage />
              </RequireAdmin>
            }
          />
          <Route path="holidays" element={<HolidaysPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="forbidden" element={<ForbiddenPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
