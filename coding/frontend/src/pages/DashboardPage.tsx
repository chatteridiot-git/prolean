import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import {
  Card,
  Col,
  Empty,
  List,
  Progress,
  Row,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ProjectOutlined,
  RadarChartOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/auth/AuthProvider';
import { AsyncState } from '@/components/AsyncState';
import { PageHeader } from '@/components/PageHeader';
import { PercentBar } from '@/components/PercentBar';
import { StatusTag } from '@/components/StatusTag';
import { useProjects } from '@/features/projects/api';
import { listTasks } from '@/features/tasks/api';
import { getTimeline } from '@/features/timeline/api';
import { formatDate } from '@/lib/dates';
import { formatPercent, gapLabel } from '@/lib/progress';
import { queryKeys } from '@/lib/queryKeys';
import type { TaskSummary, TimelineAggregate } from '@/types/api';

const SUMMARY_LIMIT = 12;

export function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const projectsQuery = useProjects({ page: 0, size: 100, status: 'ACTIVE', sort: 'name,asc' });
  const projects = useMemo(
    () => (projectsQuery.data?.content ?? []).slice(0, SUMMARY_LIMIT),
    [projectsQuery.data],
  );

  const taskQueries = useQueries({
    queries: projects.map((project) => ({
      queryKey: queryKeys.tasks.list(project.id, {
        page: 0,
        size: 5,
        picUserId: user?.id,
        status: 'OPEN',
      }),
      queryFn: () =>
        listTasks(project.id, {
          page: 0,
          size: 5,
          picUserId: user?.id,
          status: 'OPEN',
          sort: 'endDate,asc',
        }),
      enabled: Boolean(user?.id),
    })),
  });

  const timelineQueries = useQueries({
    queries: projects.map((project) => ({
      queryKey: queryKeys.timeline.aggregate(project.id),
      queryFn: () => getTimeline(project.id),
    })),
  });

  const myTasks = taskQueries
    .flatMap((query) => query.data?.content ?? [])
    .sort((a, b) => a.endDate.localeCompare(b.endDate))
    .slice(0, 6);
  const timelines = timelineQueries
    .map((query) => query.data)
    .filter((timeline): timeline is TimelineAggregate => Boolean(timeline?.current));
  const behindPlan = timelines.filter(
    (timeline) => (timeline.current?.percentActual ?? 0) < (timeline.current?.percentPlan ?? 0),
  ).length;
  const openTaskTotal = taskQueries.reduce((sum, query) => sum + (query.data?.totalElements ?? 0), 0);
  const loading =
    projectsQuery.isLoading ||
    taskQueries.some((query) => query.isLoading) ||
    timelineQueries.some((query) => query.isLoading);
  const error =
    projectsQuery.error ?? taskQueries.find((query) => query.error)?.error ??
    timelineQueries.find((query) => query.error)?.error;

  return (
    <div>
      <PageHeader
        title={`Good to see you, ${user?.fullName?.split(' ')[0] ?? 'there'}`}
        subtitle="A quick view of the projects and work that need your attention."
      />
      <AsyncState
        loading={loading}
        error={error}
        onRetry={() => {
          void projectsQuery.refetch();
          taskQueries.forEach((query) => void query.refetch());
          timelineQueries.forEach((query) => void query.refetch());
        }}
      >
        <Row gutter={[16, 16]} className="summary-grid">
          <Col xs={12} lg={6}>
            <Card className="stat-card">
              <Statistic
                title="Active projects"
                value={projectsQuery.data?.totalElements ?? 0}
                prefix={<ProjectOutlined />}
              />
              <Typography.Text type="secondary">Across your membership</Typography.Text>
            </Card>
          </Col>
          <Col xs={12} lg={6}>
            <Card className="stat-card">
              <Statistic title="My open tasks" value={openTaskTotal} prefix={<ClockCircleOutlined />} />
              <Typography.Text type="secondary">Assigned to you</Typography.Text>
            </Card>
          </Col>
          <Col xs={12} lg={6}>
            <Card className="stat-card">
              <Statistic
                title="Current timelines"
                value={timelines.length}
                prefix={<RadarChartOutlined />}
              />
              <Typography.Text type="secondary">Published baselines</Typography.Text>
            </Card>
          </Col>
          <Col xs={12} lg={6}>
            <Card className="stat-card">
              <Statistic
                title="Behind plan"
                value={behindPlan}
                prefix={<CheckCircleOutlined />}
                valueStyle={behindPlan > 0 ? { color: '#b45309' } : { color: '#0f766e' }}
              />
              <Typography.Text type="secondary">Projects to review</Typography.Text>
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} className="section-gap">
          <Col xs={24} xl={14}>
            <Card
              title="My open tasks"
              extra={
                <Typography.Link onClick={() => navigate('/projects')}>
                  Browse projects <ArrowRightOutlined />
                </Typography.Link>
              }
            >
              {myTasks.length === 0 ? (
                <Empty description="No open tasks assigned to you. Nice work!" />
              ) : (
                <List
                  dataSource={myTasks}
                  renderItem={(task: TaskSummary) => (
                    <List.Item
                      actions={[
                        <Typography.Link
                          key="open"
                          onClick={() => navigate(`/projects/${task.projectId}/tasks`)}
                        >
                          Open
                        </Typography.Link>,
                      ]}
                    >
                      <List.Item.Meta
                        title={task.title}
                        description={
                          <Space wrap size="small">
                            <StatusTag value={task.status} />
                            <span>
                              {formatDate(task.startDate)} – {formatDate(task.endDate)}
                            </span>
                            <span>{task.durationDays} days</span>
                          </Space>
                        }
                      />
                      <Progress
                        percent={task.percentComplete}
                        size="small"
                        className="task-progress-inline"
                      />
                    </List.Item>
                  )}
                />
              )}
            </Card>
          </Col>
          <Col xs={24} xl={10}>
            <Card
              title="Current timelines"
              extra={
                <Typography.Link onClick={() => navigate('/projects')}>
                  All projects <ArrowRightOutlined />
                </Typography.Link>
              }
            >
              {timelines.length === 0 ? (
                <Empty description="No published timelines yet." />
              ) : (
                <Space direction="vertical" size="middle" className="full-width">
                  {timelines.slice(0, 5).map((timeline) => (
                    <Card
                      key={timeline.timelineId}
                      size="small"
                      className="timeline-summary-card"
                      onClick={() => navigate(`/projects/${timeline.projectId}/timeline`)}
                    >
                      <Space direction="vertical" size={6} className="full-width">
                        <Space wrap>
                          <Tag color="geekblue">v{timeline.current?.versionNumber}</Tag>
                          <StatusTag value={timeline.current?.operationalStatus ?? 'OPEN'} />
                          <Typography.Text type="secondary">
                            {formatDate(timeline.current?.startDate)} –{' '}
                            {formatDate(timeline.current?.endDate)}
                          </Typography.Text>
                        </Space>
                        <PercentBar
                          percent={timeline.current?.percentActual ?? 0}
                          status={timeline.current?.operationalStatus}
                          label={`Actual vs ${formatPercent(timeline.current?.percentPlan)} plan`}
                        />
                        <Typography.Text type="secondary" className="small-text">
                          {gapLabel(
                            timeline.current?.percentActual ?? 0,
                            timeline.current?.percentPlan ?? 0,
                          )}
                        </Typography.Text>
                      </Space>
                    </Card>
                  ))}
                </Space>
              )}
            </Card>
          </Col>
        </Row>

        <Card
          title="Active projects"
          className="section-gap"
          extra={
            <Typography.Link onClick={() => navigate('/projects')}>
              View all <ArrowRightOutlined />
            </Typography.Link>
          }
        >
          {projects.length === 0 ? (
            <Empty description="You are not a member of any active projects yet." />
          ) : (
            <Row gutter={[16, 16]}>
              {projects.slice(0, 6).map((project) => (
                <Col xs={24} md={12} xl={8} key={project.id}>
                  <Card
                    hoverable
                    className="project-card"
                    onClick={() => navigate(`/projects/${project.id}/tasks`)}
                  >
                    <Space direction="vertical" size={8} className="full-width">
                      <Space wrap>
                        <Typography.Text code>{project.code}</Typography.Text>
                        <StatusTag value={project.status} />
                      </Space>
                      <Typography.Title level={5} className="project-card-title">
                        {project.name}
                      </Typography.Title>
                      <Typography.Text type="secondary">
                        {formatDate(project.startDate)} – {formatDate(project.endDate)}
                      </Typography.Text>
                      <Typography.Text type="secondary" className="small-text">
                        {project.memberCount} members
                      </Typography.Text>
                    </Space>
                  </Card>
                </Col>
              ))}
            </Row>
          )}
        </Card>
      </AsyncState>
    </div>
  );
}
