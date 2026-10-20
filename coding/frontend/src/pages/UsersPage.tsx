import { useState } from 'react';
import {
  App,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { EditOutlined, KeyOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { PageHeader } from '@/components/PageHeader';
import { ProblemAlert } from '@/components/AsyncState';
import { CreateUserDrawer } from '@/features/users/CreateUserDrawer';
import {
  useChangeUserStatus,
  useCreateUser,
  useReplaceRoles,
  useSetUserPassword,
  useUpdateUser,
  useUsers,
} from '@/features/users/api';
import { problemMessage } from '@/lib/errors';
import { passwordPolicy } from '@/lib/validation';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import type { RoleName, User } from '@/types/api';
import type { CreateUserValues } from '@/lib/validation';

function EditUserModal({
  user,
  onClose,
}: {
  user: User | null;
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const updateUser = useUpdateUser(user?.id ?? '');

  const open = Boolean(user);
  const save = async () => {
    try {
      await updateUser.mutateAsync({ email, fullName });
      message.success('User updated');
      onClose();
    } catch (error) {
      message.error(problemMessage(error));
    }
  };

  return (
    <Modal
      title={user ? `Edit ${user.username}` : 'Edit user'}
      open={open}
      onCancel={onClose}
      onOk={() => void save()}
      okText="Save"
      confirmLoading={updateUser.isPending}
      afterOpenChange={(visible) => {
        if (visible && user) {
          setEmail(user.email);
          setFullName(user.fullName);
        }
      }}
    >
      <Form layout="vertical">
        <Form.Item label="Full name">
          <Input value={fullName} onChange={(event) => setFullName(event.target.value)} maxLength={100} />
        </Form.Item>
        <Form.Item label="Email">
          <Input value={email} onChange={(event) => setEmail(event.target.value)} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function RolesModal({ user, onClose }: { user: User | null; onClose: () => void }) {
  const { message } = App.useApp();
  const [roles, setRoles] = useState<RoleName[]>(['USER']);
  const replaceRoles = useReplaceRoles(user?.id ?? '');
  const open = Boolean(user);

  const save = async () => {
    if (roles.length === 0) {
      message.error('Choose at least one role.');
      return;
    }
    try {
      await replaceRoles.mutateAsync(roles);
      message.success('Roles updated');
      onClose();
    } catch (error) {
      message.error(problemMessage(error));
    }
  };

  return (
    <Modal
      title={user ? `Roles for ${user.username}` : 'Roles'}
      open={open}
      onCancel={onClose}
      onOk={() => void save()}
      okText="Save roles"
      confirmLoading={replaceRoles.isPending}
      afterOpenChange={(visible) => {
        if (visible && user) setRoles(user.roles);
      }}
    >
      <Checkbox.Group
        value={roles}
        onChange={(values) => setRoles(values as RoleName[])}
        options={[
          { value: 'USER', label: 'User' },
          { value: 'ADMIN', label: 'Administrator' },
        ]}
      />
      <Typography.Paragraph type="secondary" className="small-text section-gap">
        Administrators can create projects, manage users, and maintain holidays. At least one role is
        required.
      </Typography.Paragraph>
    </Modal>
  );
}

function PasswordModal({ user, onClose }: { user: User | null; onClose: () => void }) {
  const { message } = App.useApp();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const setPasswordMutation = useSetUserPassword();

  const save = async () => {
    const parsed = passwordPolicy.safeParse(password);
    if (!parsed.success) {
      message.error(parsed.error.issues[0]?.message ?? 'Invalid password');
      return;
    }
    if (password !== confirm) {
      message.error('Passwords do not match');
      return;
    }
    if (!user) return;
    try {
      await setPasswordMutation.mutateAsync({ userId: user.id, newPassword: password });
      message.success('Password set. Existing sessions were signed out.');
      onClose();
    } catch (error) {
      message.error(problemMessage(error));
    }
  };

  return (
    <Modal
      title={user ? `Set password for ${user.username}` : 'Set password'}
      open={Boolean(user)}
      onCancel={onClose}
      onOk={() => void save()}
      okText="Set password"
      confirmLoading={setPasswordMutation.isPending}
      afterOpenChange={(visible) => {
        if (visible) {
          setPassword('');
          setConfirm('');
        }
      }}
    >
      <Form layout="vertical">
        <Form.Item label="New password">
          <Input.Password value={password} onChange={(event) => setPassword(event.target.value)} />
        </Form.Item>
        <Form.Item label="Confirm password">
          <Input.Password value={confirm} onChange={(event) => setConfirm(event.target.value)} />
        </Form.Item>
      </Form>
      <Typography.Paragraph type="secondary" className="small-text">
        8–64 characters with at least one letter and one digit. All refresh-token families for this
        user are revoked.
      </Typography.Paragraph>
    </Modal>
  );
}

export function UsersPage() {
  const { message } = App.useApp();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [search, setSearch] = useState('');
  const [enabled, setEnabled] = useState<boolean | undefined>();
  const [role, setRole] = useState<RoleName | undefined>();
  const debouncedSearch = useDebouncedValue(search);
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<User | null>(null);
  const [rolesUser, setRolesUser] = useState<User | null>(null);
  const [passwordUser, setPasswordUser] = useState<User | null>(null);
  const usersQuery = useUsers({
    page,
    size,
    q: debouncedSearch || undefined,
    enabled,
    role,
    sort: 'fullName,asc',
  });
  const createUser = useCreateUser();
  const statusMutation = useChangeUserStatus();
  const setPassword = useSetUserPassword();

  const create = async (values: CreateUserValues) => {
    const created = await createUser.mutateAsync({
      username: values.username,
      email: values.email,
      fullName: values.fullName,
      roles: values.roles,
    });
    try {
      await setPassword.mutateAsync({ userId: created.id, newPassword: values.password });
      message.success('User created');
      setCreateOpen(false);
    } catch (error) {
      message.warning(
        `User created, but the password could not be set: ${problemMessage(error)}. Use “Set password”.`,
      );
      setCreateOpen(false);
    }
  };

  const toggleEnabled = async (user: User, next: boolean) => {
    try {
      await statusMutation.mutateAsync({ userId: user.id, enabled: next });
      message.success(next ? 'User activated' : 'User deactivated');
    } catch (error) {
      message.error(problemMessage(error));
    }
  };

  const columns: ColumnsType<User> = [
    {
      title: 'User',
      dataIndex: 'fullName',
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{value}</Typography.Text>
          <Typography.Text type="secondary" className="small-text">
            @{record.username}
          </Typography.Text>
        </Space>
      ),
    },
    { title: 'Email', dataIndex: 'email', responsive: ['md'] },
    {
      title: 'Roles',
      dataIndex: 'roles',
      width: 170,
      render: (roles: RoleName[]) => (
        <Space wrap size={4}>
          {roles.map((value) => (
            <Tag key={value} color={value === 'ADMIN' ? 'purple' : 'blue'}>
              {value === 'ADMIN' ? 'Administrator' : 'User'}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Active',
      dataIndex: 'enabled',
      width: 90,
      render: (value: boolean, record) => (
        <Switch
          checked={value}
          onChange={(next) => void toggleEnabled(record, next)}
          aria-label={`Toggle ${record.username}`}
        />
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 150,
      render: (_, record) => (
        <Space>
          <Button
            type="text"
            icon={<EditOutlined />}
            aria-label={`Edit ${record.username}`}
            onClick={() => setEditUser(record)}
          />
          <Button
            type="text"
            icon={<KeyOutlined />}
            aria-label={`Set password for ${record.username}`}
            onClick={() => setPasswordUser(record)}
          />
          <Button type="link" onClick={() => setRolesUser(record)}>
            Roles
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Users"
        subtitle="Create profiles, assign roles, and set passwords. Passwords are stored only by the auth service."
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            New user
          </Button>
        }
      />
      <Card>
        <Space wrap className="filter-bar">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="Search name, username or email"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            className="search-input-wide"
          />
          <Select
            allowClear
            placeholder="Any state"
            value={enabled}
            onChange={(value) => {
              setEnabled(value);
              setPage(0);
            }}
            options={[
              { value: true, label: 'Active' },
              { value: false, label: 'Inactive' },
            ]}
            className="filter-select"
          />
          <Select
            allowClear
            placeholder="Any role"
            value={role}
            onChange={(value) => {
              setRole(value);
              setPage(0);
            }}
            options={[
              { value: 'ADMIN', label: 'Administrator' },
              { value: 'USER', label: 'User' },
            ]}
            className="filter-select"
          />
          <Tag>{usersQuery.data?.totalElements ?? 0} users</Tag>
        </Space>
        {usersQuery.error ? <ProblemAlert error={usersQuery.error} /> : null}
        <Table<User>
          rowKey="id"
          columns={columns}
          dataSource={usersQuery.data?.content ?? []}
          loading={usersQuery.isLoading}
          scroll={{ x: 760 }}
          locale={{ emptyText: 'No users match these filters.' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: usersQuery.data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            showTotal: (total) => `${total} users`,
            onChange: (nextPage, nextSize) => {
              setPage(nextPage - 1);
              setSize(nextSize);
            },
          }}
        />
      </Card>
      <CreateUserDrawer
        open={createOpen}
        submitting={createUser.isPending || setPassword.isPending}
        onClose={() => setCreateOpen(false)}
        onSubmit={create}
      />
      <EditUserModal user={editUser} onClose={() => setEditUser(null)} />
      <RolesModal user={rolesUser} onClose={() => setRolesUser(null)} />
      <PasswordModal user={passwordUser} onClose={() => setPasswordUser(null)} />
    </div>
  );
}
