import { useMemo } from 'react';
import { Outlet, useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { Alert, Button, Space, Tabs, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { AsyncState } from '@/components/AsyncState';
import { PageHeader } from '@/components/PageHeader';
import { StatusTag } from '@/components/StatusTag';
import { useProject } from '@/features/projects/api';
import { formatDate } from '@/lib/dates';
import type { Project } from '@/types/api';

export interface ProjectContext {
  project: Project;
}

export function useProjectContext(): ProjectContext {
  return useOutletContext<ProjectContext>();
}

export function ProjectLayout() {
  const { projectId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const projectQuery = useProject(projectId);

  const activeTab = useMemo(() => {
    if (location.pathname.endsWith('/timeline')) return 'timeline';
    if (location.pathname.endsWith('/members')) return 'members';
    return 'tasks';
  }, [location.pathname]);

  return (
    <div>
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/projects')}
        className="back-button"
      >
        All projects
      </Button>
      <AsyncState
        loading={projectQuery.isLoading}
        error={projectQuery.error}
        onRetry={() => void projectQuery.refetch()}
      >
        {projectQuery.data ? (
          <>
            <PageHeader
              title={
                <Space wrap size="middle">
                  <span>{projectQuery.data.name}</span>
                  <StatusTag value={projectQuery.data.status} />
                </Space>
              }
              subtitle={
                <span>
                  <Typography.Text code>{projectQuery.data.code}</Typography.Text>
                  {' · '}
                  {formatDate(projectQuery.data.startDate)} – {formatDate(projectQuery.data.endDate)}
                  {' · '}
                  {projectQuery.data.memberCount} members
                </span>
              }
            />
            {projectQuery.data.status === 'ARCHIVED' ? (
              <Alert
                type="warning"
                showIcon
                message="This project is archived"
                description="Task and timeline changes are read-only. An administrator can reactivate it from the projects screen."
                className="section-gap"
              />
            ) : null}
            <Tabs
              activeKey={activeTab}
              onChange={(key) => navigate(`/projects/${projectId}/${key}`)}
              items={[
                { key: 'tasks', label: 'Tasks' },
                { key: 'timeline', label: 'Timeline' },
                { key: 'members', label: 'Members' },
              ]}
              className="project-tabs"
            />
            <Outlet context={{ project: projectQuery.data } satisfies ProjectContext} />
          </>
        ) : null}
      </AsyncState>
    </div>
  );
}
