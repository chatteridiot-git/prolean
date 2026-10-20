import { useState } from 'react';
import {
  App,
  Avatar,
  Button,
  Card,
  Dropdown,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useProjectContext } from '@/app/ProjectLayout';
import { PageHeader } from '@/components/PageHeader';
import { ProblemAlert } from '@/components/AsyncState';
import { PercentBar } from '@/components/PercentBar';
import { StatusTag } from '@/components/StatusTag';
import { useProjectMembers } from '@/features/projects/api';
import {
  getTask,
  useChangeTaskProgress,
  useChangeTaskStatus,
  useCreateTask,
  useDeleteTask,
  useTasks,
  useUpdateTask,
} from '@/features/tasks/api';
import { TaskFormDrawer } from '@/features/tasks/TaskFormDrawer';
import { problemCode, problemMessage } from '@/lib/errors';
import { formatDate } from '@/lib/dates';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import type { ProjectMember, Task, TaskStatus, TaskSummary } from '@/types/api';
import type { TaskValues } from '@/lib/validation';

function initials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
}

function TaskRowActions({
  projectId,
  task,
  disabled,
  onEdit,
  onProgress,
}: {
  projectId: string;
  task: TaskSummary;
  disabled: boolean;
  onEdit: () => void;
  onProgress: () => void;
}) {
  const { message, modal } = App.useApp();
  const statusMutation = useChangeTaskStatus(projectId);
  const deleteMutation = useDeleteTask(projectId);
  const statuses: TaskStatus[] = ['OPEN', 'IN_PROGRESS', 'CLOSED'];

  const changeStatus = async (status: TaskStatus) => {
    try {
      await statusMutation.mutateAsync({ taskId: task.id, status });
      message.success('Task status updated');
    } catch (error) {
      message.error(problemMessage(error));
    }
  };

  const remove = () => {
    modal.confirm({
      title: 'Delete this task?',
      content: 'This permanently removes the task. Timeline history is not affected.',
      okText: 'Delete',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteMutation.mutateAsync(task.id);
          message.success('Task deleted');
        } catch (error) {
          message.error(problemMessage(error));
          throw error;
        }
      },
    });
  };

  return (
    <Dropdown
      trigger={['click']}
      disabled={disabled}
      menu={{
        items: [
          { key: 'edit', label: 'Edit task', icon: <EditOutlined />, onClick: onEdit },
          { key: 'progress', label: 'Update progress', onClick: onProgress },
          { type: 'divider' },
          ...statuses.map((status) => ({
            key: status,
            label: `Mark ${status.replace('_', ' ').toLowerCase()}`,
            disabled: task.status === status,
            onClick: () => void changeStatus(status),
          })),
          { type: 'divider' },
          {
            key: 'delete',
            label: 'Delete task',
            icon: <DeleteOutlined />,
            danger: true,
            onClick: remove,
          },
        ],
      }}
    >
      <Button type="text" icon={<DownOutlined />} aria-label={`Actions for ${task.title}`} />
    </Dropdown>
  );
}

export function TasksPage() {
  const { project } = useProjectContext();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const archived = project.status === 'ARCHIVED';
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<TaskStatus | undefined>();
  const [picUserId, setPicUserId] = useState<string | undefined>();
  const debouncedSearch = useDebouncedValue(search);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Task | null>(null);
  const [progressTask, setProgressTask] = useState<TaskSummary | null>(null);
  const [progressValue, setProgressValue] = useState(0);

  const membersQuery = useProjectMembers(project.id);
  const members: ProjectMember[] = membersQuery.data?.content ?? [];
  const filters = {
    page,
    size,
    q: debouncedSearch || undefined,
    status,
    picUserId,
    sort: 'createdAt,desc',
  };
  const tasksQuery = useTasks(project.id, filters);
  const createTask = useCreateTask(project.id);
  const updateTask = useUpdateTask(project.id, editing?.id ?? '');
  const progressMutation = useChangeTaskProgress(project.id);

  const openCreate = () => {
    setEditing(null);
    setDrawerOpen(true);
  };

  const openEdit = async (taskId: string) => {
    try {
      const task = await queryClient.fetchQuery({
        queryKey: ['projects', project.id, 'tasks', taskId],
        queryFn: () => getTask(project.id, taskId),
      });
      setEditing(task);
      setDrawerOpen(true);
    } catch (error) {
      message.error(problemMessage(error));
    }
  };

  const submit = async (values: TaskValues) => {
    if (editing) {
      await updateTask.mutateAsync(values);
      message.success('Task updated');
    } else {
      await createTask.mutateAsync(values);
      message.success('Task created');
    }
    setDrawerOpen(false);
    setEditing(null);
  };

  const openProgress = (task: TaskSummary) => {
    setProgressTask(task);
    setProgressValue(task.percentComplete);
  };

  const submitProgress = async () => {
    if (!progressTask) return;
    try {
      await progressMutation.mutateAsync({
        taskId: progressTask.id,
        percentComplete: progressValue,
      });
      message.success('Progress updated');
      setProgressTask(null);
    } catch (error) {
      message.error(
        problemCode(error) === 'TASK_CLOSED'
          ? 'Closed tasks cannot change progress. Reopen the task first.'
          : problemMessage(error),
      );
    }
  };

  const columns: ColumnsType<TaskSummary> = [
    {
      title: 'Task',
      dataIndex: 'title',
      render: (value: string, record) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{value}</Typography.Text>
          <Typography.Text type="secondary" className="small-text">
            {formatDate(record.startDate)} – {formatDate(record.endDate)} · {record.durationDays}{' '}
            calendar days
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'PIC',
      dataIndex: 'pic',
      width: 180,
      responsive: ['md'],
      render: (_, record) => (
        <Space>
          <Avatar size="small">{initials(record.pic.name)}</Avatar>
          <span>{record.pic.name}</span>
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (value: TaskStatus) => <StatusTag value={value} />,
    },
    {
      title: 'Progress',
      dataIndex: 'percentComplete',
      width: 220,
      render: (value: number, record) => (
        <PercentBar percent={value} status={record.status} />
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 64,
      render: (_, record) => (
        <TaskRowActions
          projectId={project.id}
          task={record}
          disabled={archived}
          onEdit={() => void openEdit(record.id)}
          onProgress={() => openProgress(record)}
        />
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Tasks"
        subtitle="Inclusive calendar-day duration is calculated for you. Closed tasks must be 100% complete."
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={archived || members.length === 0}
            onClick={openCreate}
          >
            New task
          </Button>
        }
      />
      {archived ? (
        <Typography.Paragraph type="secondary">
          This project is archived, so task editing is disabled.
        </Typography.Paragraph>
      ) : null}
      <Card>
        <Space wrap className="filter-bar">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="Search title"
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
              { value: 'OPEN', label: 'Open' },
              { value: 'IN_PROGRESS', label: 'In progress' },
              { value: 'CLOSED', label: 'Closed' },
            ]}
            className="filter-select"
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="All PICs"
            value={picUserId}
            onChange={(value) => {
              setPicUserId(value);
              setPage(0);
            }}
            options={members.map((member) => ({
              value: member.userId,
              label: member.fullName,
            }))}
            className="filter-select-wide"
          />
          <Tag>{tasksQuery.data?.totalElements ?? 0} tasks</Tag>
        </Space>
        {membersQuery.error ? <ProblemAlert error={membersQuery.error} title="Could not load members" /> : null}
        {!membersQuery.isLoading && members.length === 0 ? (
          <Typography.Paragraph type="warning">
            Add at least one project member before creating tasks.
          </Typography.Paragraph>
        ) : null}
        {tasksQuery.error ? <ProblemAlert error={tasksQuery.error} /> : null}
        <Table<TaskSummary>
          rowKey="id"
          columns={columns}
          dataSource={tasksQuery.data?.content ?? []}
          loading={tasksQuery.isLoading}
          scroll={{ x: 780 }}
          locale={{ emptyText: 'No tasks match these filters.' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: tasksQuery.data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            showTotal: (total) => `${total} tasks`,
            onChange: (nextPage, nextSize) => {
              setPage(nextPage - 1);
              setSize(nextSize);
            },
          }}
        />
      </Card>
      <TaskFormDrawer
        open={drawerOpen}
        task={editing}
        members={members}
        submitting={createTask.isPending || updateTask.isPending}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
        onSubmit={submit}
      />
      <Modal
        title="Update task progress"
        open={Boolean(progressTask)}
        onCancel={() => setProgressTask(null)}
        onOk={() => void submitProgress()}
        okText="Save progress"
        confirmLoading={progressMutation.isPending}
      >
        <Typography.Paragraph type="secondary">
          {progressTask?.title} · currently {progressTask?.percentComplete ?? 0}%
        </Typography.Paragraph>
        <InputNumber
          min={0}
          max={100}
          addonAfter="%"
          value={progressValue}
          onChange={(value) => setProgressValue(Number(value ?? 0))}
          className="full-width"
        />
      </Modal>
    </div>
  );
}
