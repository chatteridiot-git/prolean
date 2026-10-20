import { useMemo, useState } from 'react';
import {
  App,
  Avatar,
  Button,
  Card,
  Modal,
  Select,
  Space,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, UserAddOutlined } from '@ant-design/icons';
import { useProjectContext } from '@/app/ProjectLayout';
import { useAuth } from '@/auth/AuthProvider';
import { PageHeader } from '@/components/PageHeader';
import { ProblemAlert } from '@/components/AsyncState';
import { useAddMembers, useProjectMembers, useRemoveMember } from '@/features/projects/api';
import { useUsers } from '@/features/users/api';
import { formatDateTime } from '@/lib/dates';
import type { ProjectMember } from '@/types/api';

export function MembersPage() {
  const { project } = useProjectContext();
  const { isAdmin, user } = useAuth();
  const { message, modal } = App.useApp();
  const archived = project.status === 'ARCHIVED';
  const [page, setPage] = useState(0);
  const [addOpen, setAddOpen] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const membersQuery = useProjectMembers(project.id, page);
  const usersQuery = useUsers({ page: 0, size: 100, enabled: true, sort: 'fullName,asc' });
  const addMembers = useAddMembers(project.id);
  const removeMember = useRemoveMember(project.id);

  const memberIds = useMemo(
    () => new Set((membersQuery.data?.content ?? []).map((member) => member.userId)),
    [membersQuery.data],
  );

  const add = async () => {
    try {
      const result = await addMembers.mutateAsync(selected);
      message.success(
        `${result.added} member${result.added === 1 ? '' : 's'} added` +
          (result.alreadyMembers ? `, ${result.alreadyMembers} already present` : ''),
      );
      setAddOpen(false);
      setSelected([]);
    } catch (error) {
      message.error('Could not add the selected members.');
      throw error;
    }
  };

  const remove = (member: ProjectMember) => {
    modal.confirm({
      title: `Remove ${member.fullName}?`,
      content:
        member.userId === user?.id
          ? 'You are removing yourself. You will lose access to this project immediately.'
          : 'The user will lose access to this project. Existing task PIC snapshots are retained.',
      okText: 'Remove',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await removeMember.mutateAsync(member.userId);
          message.success('Member removed');
        } catch (error) {
          message.error('Could not remove the member.');
          throw error;
        }
      },
    });
  };

  const columns: ColumnsType<ProjectMember> = [
    {
      title: 'Member',
      dataIndex: 'fullName',
      render: (value: string, record) => (
        <Space>
          <Avatar size="small">{value.slice(0, 1).toUpperCase()}</Avatar>
          <Space direction="vertical" size={0}>
            <Typography.Text strong>{value}</Typography.Text>
            <Typography.Text type="secondary" className="small-text">
              @{record.username}
            </Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: 'Email',
      dataIndex: 'email',
      responsive: ['md'],
    },
    {
      title: 'Added',
      dataIndex: 'addedAt',
      responsive: ['lg'],
      render: (value: string) => formatDateTime(value),
    },
    ...(isAdmin
      ? [
          {
            title: '',
            key: 'actions',
            width: 64,
            render: (_: unknown, record: ProjectMember) => (
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                aria-label={`Remove ${record.fullName}`}
                disabled={archived || removeMember.isPending}
                onClick={() => remove(record)}
              />
            ),
          },
        ]
      : []),
  ];

  return (
    <div>
      <PageHeader
        title="Members"
        subtitle="Only project members can view and change this project's tasks and timeline."
        extra={
          isAdmin ? (
            <Button
              type="primary"
              icon={<UserAddOutlined />}
              disabled={archived}
              onClick={() => setAddOpen(true)}
            >
              Add members
            </Button>
          ) : undefined
        }
      />
      {archived ? (
        <Typography.Paragraph type="secondary">
          This project is archived; membership is read-only.
        </Typography.Paragraph>
      ) : null}
      <Card>
        {membersQuery.error ? <ProblemAlert error={membersQuery.error} /> : null}
        <Table<ProjectMember>
          rowKey="userId"
          columns={columns}
          dataSource={membersQuery.data?.content ?? []}
          loading={membersQuery.isLoading}
          scroll={{ x: 620 }}
          locale={{ emptyText: 'No members yet.' }}
          pagination={{
            current: page + 1,
            pageSize: 100,
            total: membersQuery.data?.totalElements ?? 0,
            hideOnSinglePage: true,
            showTotal: (total) => `${total} members`,
            onChange: (nextPage) => setPage(nextPage - 1),
          }}
        />
      </Card>
      <Modal
        title="Add project members"
        open={addOpen}
        onCancel={() => {
          setAddOpen(false);
          setSelected([]);
        }}
        onOk={() => void add()}
        okText="Add members"
        confirmLoading={addMembers.isPending}
      >
        <Typography.Paragraph type="secondary">
          Choose active users. Users who already belong to the project are ignored.
        </Typography.Paragraph>
        <Select
          mode="multiple"
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="Search active users"
          className="full-width"
          value={selected}
          onChange={setSelected}
          loading={usersQuery.isLoading}
          options={(usersQuery.data?.content ?? [])
            .filter((candidate) => candidate.enabled && !memberIds.has(candidate.id))
            .map((candidate) => ({
              value: candidate.id,
              label: `${candidate.fullName} (${candidate.username})`,
            }))}
        />
      </Modal>
    </div>
  );
}
