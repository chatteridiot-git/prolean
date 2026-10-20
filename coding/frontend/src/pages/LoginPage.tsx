import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Card, Form, Input, Space, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useAuth } from '@/auth/AuthProvider';
import { problemMessage } from '@/lib/errors';
import { loginSchema } from '@/lib/validation';
import type { LoginValues } from '@/lib/validation';

export function LoginPage() {
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<unknown>(null);
  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: '', password: '' },
  });

  if (status === 'authenticated') return <Navigate to="/" replace />;

  const onSubmit = async (values: LoginValues) => {
    setError(null);
    try {
      await login(values.username.trim(), values.password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : '/', { replace: true });
    } catch (cause) {
      setError(cause);
    }
  };

  return (
    <div className="login-page">
      <div className="login-hero">
        <div className="brand brand-large">
          <span className="brand-mark">P</span>
          <span className="brand-text">ProLean</span>
        </div>
        <Typography.Title level={1} className="login-heading">
          Projects, tasks, and time — finally in step.
        </Typography.Title>
        <Typography.Paragraph className="login-copy">
          Keep your team aligned with automatic task durations, working-day timelines, and a clear
          plan-versus-actual view.
        </Typography.Paragraph>
        <ul className="login-points">
          <li>Versioned project timelines with an audit trail</li>
          <li>Holiday-aware scheduling for Indonesian teams</li>
          <li>Role-based access for administrators and members</li>
        </ul>
      </div>
      <Card className="login-card" bordered={false}>
        <Typography.Title level={3}>Sign in</Typography.Title>
        <Typography.Paragraph type="secondary">
          Use the account provided by your administrator.
        </Typography.Paragraph>
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
          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <Form.Item
              label="Username"
              htmlFor="username"
              validateStatus={errors.username ? 'error' : undefined}
              help={errors.username?.message}
            >
              <Controller
                name="username"
                control={control}
                render={({ field }) => (
                  <Input
                    {...field}
                    id="username"
                    autoComplete="username"
                    prefix={<UserOutlined />}
                    placeholder="your.username"
                    size="large"
                  />
                )}
              />
            </Form.Item>
            <Form.Item
              label="Password"
              htmlFor="password"
              validateStatus={errors.password ? 'error' : undefined}
              help={errors.password?.message}
            >
              <Controller
                name="password"
                control={control}
                render={({ field }) => (
                  <Input.Password
                    {...field}
                    id="password"
                    autoComplete="current-password"
                    prefix={<LockOutlined />}
                    placeholder="Your password"
                    size="large"
                  />
                )}
              />
            </Form.Item>
            <Space direction="vertical" size="middle" className="full-width">
              <Button type="primary" htmlType="submit" size="large" block loading={isSubmitting}>
                Sign in
              </Button>
              <Typography.Text type="secondary" className="small-text">
                Sessions refresh automatically. If you are locked out after repeated attempts,
                wait 15 minutes or contact an administrator.
              </Typography.Text>
            </Space>
          </form>
        </Form>
      </Card>
    </div>
  );
}
