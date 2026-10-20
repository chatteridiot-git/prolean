import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, App, Button, Card, Col, Descriptions, Form, Input, Row, Space, Tag } from 'antd';
import { LockOutlined } from '@ant-design/icons';
import { useAuth } from '@/auth/AuthProvider';
import { PageHeader } from '@/components/PageHeader';
import { changeOwnPassword } from '@/features/users/api';
import { problemMessage } from '@/lib/errors';
import { changePasswordSchema } from '@/lib/validation';
import type { z } from 'zod';

type ChangePasswordValues = z.infer<typeof changePasswordSchema>;

export function ProfilePage() {
  const { user, clearSession } = useAuth();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [error, setError] = useState<unknown>(null);
  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '' },
  });

  const submit = async (values: ChangePasswordValues) => {
    setError(null);
    try {
      await changeOwnPassword(values.currentPassword, values.newPassword);
      message.success('Password changed. Please sign in again.');
      clearSession();
      navigate('/login', { replace: true });
    } catch (cause) {
      setError(cause);
    }
  };

  return (
    <div>
      <PageHeader
        title="My profile"
        subtitle="Your identity comes from the access token issued by the auth service."
      />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="Account">
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Full name">{user?.fullName}</Descriptions.Item>
              <Descriptions.Item label="Username">@{user?.username}</Descriptions.Item>
              <Descriptions.Item label="User ID">{user?.id}</Descriptions.Item>
              <Descriptions.Item label="Roles">
                <Space wrap>
                  {(user?.roles ?? []).map((role) => (
                    <Tag key={role} color={role === 'ADMIN' ? 'purple' : 'blue'}>
                      {role === 'ADMIN' ? 'Administrator' : 'User'}
                    </Tag>
                  ))}
                </Space>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Change password">
            {error ? (
              <Alert
                type="error"
                showIcon
                message={problemMessage(error)}
                className="section-gap"
                closable
                onClose={() => setError(null)}
              />
            ) : null}
            <Form component={false} layout="vertical" requiredMark={false}>
              <form
                onSubmit={handleSubmit(submit)}
                noValidate
                onReset={() => {
                  reset();
                  setError(null);
                }}
              >
                <Form.Item
                  label="Current password"
                  validateStatus={errors.currentPassword ? 'error' : undefined}
                  help={errors.currentPassword?.message}
                >
                  <Controller
                    name="currentPassword"
                    control={control}
                    render={({ field }) => (
                      <Input.Password
                        {...field}
                        prefix={<LockOutlined />}
                        autoComplete="current-password"
                      />
                    )}
                  />
                </Form.Item>
                <Form.Item
                  label="New password"
                  validateStatus={errors.newPassword ? 'error' : undefined}
                  help={errors.newPassword?.message}
                >
                  <Controller
                    name="newPassword"
                    control={control}
                    render={({ field }) => (
                      <Input.Password
                        {...field}
                        prefix={<LockOutlined />}
                        autoComplete="new-password"
                      />
                    )}
                  />
                </Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" loading={isSubmitting}>
                    Change password
                  </Button>
                  <Button htmlType="reset">Clear</Button>
                </Space>
              </form>
            </Form>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
