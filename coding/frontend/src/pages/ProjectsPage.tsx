import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  App,
  Button,
  Card,
  Dropdown,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DownOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useAuth } from '@/auth/AuthProvider';
import { PageHeader } from '@/components/PageHeader';
import { ProblemAlert } from '@/components/AsyncState';
import { StatusTag } from '@/components/StatusTag';
import {
  useChangeProjectStatus,
  useCreateProject,
  useProjects,
} from '@/features/projects/api';
import { ProjectFormDrawer } from '@/features/projects/ProjectFormDrawer';
import type { ProjectValues } from '@/lib/validation';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import { formatDate } from '@/lib/dates';
import type { ProjectStatus, ProjectSummary } from '@/types/api';

export function ProjectsPage() {
  const { isAdmin } = useAuth();
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ProjectStatus | undefined>();
  const debouncedSearch = useDebouncedValue(search);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const filters = {
    page,
    size,
    q: debouncedSearch || undefined,
    status,
    sort: 'createdAt,desc',
  };
  const projectsQuery = useProjects(filters);
  const createProject = useCreateProject();
  const statusMutation = useChangeProjectStatus();

  const create = async (values: ProjectValues) => {
    try {
      await createProject.mutateAsync({
        code: values.code,
        name: values.name,
        description: values.description,
        startDate: values.startDate,
        endDate: values.endDate,
        memberIds: values.memberIds,
      });
      setDrawerOpen(false);
      message.success('Project created');
    } catch (cause) {
      message.error('Could not create the project.');
      throw cause;
    }
  };

  const columns: ColumnsType<ProjectSummary> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 130,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: 'Project',
      dataIndex: 'name',
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          <Typography.Text type="secondary" className="small-text">
            {record.memberCount} members
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Period',
      key: 'period',
      responsive: ['md'],
      render: (_, record) => (
        <span>
          {formatDate(record.startDate)} – {formatDate(record.endDate)}
        </span>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (value: ProjectStatus) => <StatusTag value={value} />,
    },
    ...(isAdmin
      ? [
          {
            title: '',
            key: 'actions',
            width: 90,
            render: (_: unknown, record: ProjectSummary) => (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    {
                      key: 'open',
                      label: 'Open project',
                      onClick: () => navigate(`/projects/${record.id}/tasks`),
                    },
                    {
                      key: 'status',
                      label: record.status === 'ACTIVE' ? 'Archive project' : 'Reactivate project',
                      danger: record.status === 'ACTIVE',
                      onClick: () => {
                        const next: ProjectStatus =
                          record.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE';
                        modal.confirm({
                          title:
                            next === 'ARCHIVED' ? 'Archive this project?' : 'Reactivate this project?',
                          content:
                            next === 'ARCHIVED'
                              ? 'Archived projects are read-only. Timeline history is retained.'
                              : 'The project will accept task and timeline changes again.',
                          okText: next === 'ARCHIVED' ? 'Archive' : 'Reactivate',
                          okButtonProps: { danger: next === 'ARCHIVED' },
                          onOk: async () => {
                            try {
                              await statusMutation.mutateAsync({ projectId: record.id, status: next });
                              message.success(
                                next === 'ARCHIVED' ? 'Project archived' : 'Project reactivated',
                              );
                              void projectsQuery.refetch();
                            } catch (error) {
                              message.error('Could not change the project status.');
                              throw error;
                            }
                          },
                        });
                      },
                    },
                  ],
                }}
              >
                <Button type="text" icon={<DownOutlined />} aria-label="Project actions" />
              </Dropdown>
            ),
          },
        ]
      : []),
  ];

  return (
    <div>
      <PageHeader
        title="Projects"
        subtitle="Projects you belong to. Administrators can create projects and manage membership."
        extra={
          isAdmin ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setDrawerOpen(true)}>
              New project
            </Button>
          ) : undefined
        }
      />
      <Card>
        <Space wrap className="filter-bar">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="Search name or code"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            className="search-input"
          />
          <Select
            allowClear
            placeholder="All statuses"
            value={status}
            onChange={(value) => {
              setStatus(value);
              setPage(0);
            }}
            options={[
              { value: 'ACTIVE', label: 'Active' },
              { value: 'ARCHIVED', label: 'Archived' },
            ]}
            className="filter-select"
          />
          <Tag>{projectsQuery.data?.totalElements ?? 0} projects</Tag>
        </Space>
        {projectsQuery.error ? <ProblemAlert error={projectsQuery.error} /> : null}
        <Table<ProjectSummary>
          rowKey="id"
          columns={columns}
          dataSource={projectsQuery.data?.content ?? []}
          loading={projectsQuery.isLoading}
          scroll={{ x: 720 }}
          onRow={(record) => ({
            onDoubleClick: () => navigate(`/projects/${record.id}/tasks`),
            style: { cursor: 'pointer' },
          })}
          locale={{ emptyText: 'No projects match these filters.' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: projectsQuery.data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            showTotal: (total) => `${total} projects`,
            onChange: (nextPage, nextSize) => {
              setPage(nextPage - 1);
              setSize(nextSize);
            },
          }}
        />
      </Card>
      <ProjectFormDrawer
        open={drawerOpen}
        submitting={createProject.isPending}
        onClose={() => setDrawerOpen(false)}
        onSubmit={create}
      />
    </div>
  );
}
