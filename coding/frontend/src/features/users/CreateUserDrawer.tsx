import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Drawer, Form, Input, Select, Space } from 'antd';
import { problemMessage } from '@/lib/errors';
import { createUserSchema } from '@/lib/validation';
import type { CreateUserValues } from '@/lib/validation';
import type { RoleName } from '@/types/api';

export function CreateUserDrawer({
  open,
  submitting,
  onClose,
  onSubmit,
}: {
  open: boolean;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (values: CreateUserValues) => Promise<void>;
}) {
  const [error, setError] = useState<unknown>(null);
  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateUserValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      username: '',
      email: '',
      fullName: '',
      roles: ['USER'],
      password: '',
      confirmPassword: '',
    },
  });

  useEffect(() => {
    if (!open) return;
    setError(null);
    reset({
      username: '',
      email: '',
      fullName: '',
      roles: ['USER'],
      password: '',
      confirmPassword: '',
    });
  }, [open, reset]);

  const submit = async (values: CreateUserValues) => {
    setError(null);
    try {
      await onSubmit(values);
    } catch (cause) {
      setError(cause);
    }
  };

  return (
    <Drawer
      title="Create user"
      open={open}
      onClose={onClose}
      width={520}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" htmlType="submit" form="create-user-form" loading={submitting}>
            Create user
          </Button>
        </Space>
      }
    >
      {error ? (
        <Alert
          type="error"
          showIcon
          message={problemMessage(error)}
          description={
            problemMessage(error).includes('password')
              ? 'The profile may have been created. Use “Set password” in the user list to finish.'
              : undefined
          }
          className="section-gap"
          closable
          onClose={() => setError(null)}
        />
      ) : null}
      <Form component={false} layout="vertical" requiredMark={false}>
        <form id="create-user-form" onSubmit={handleSubmit(submit)} noValidate>
          <Form.Item
            label="Username"
            validateStatus={errors.username ? 'error' : undefined}
            help={errors.username?.message}
          >
            <Controller
              name="username"
              control={control}
              render={({ field }) => <Input {...field} maxLength={50} placeholder="jane.doe" />}
            />
          </Form.Item>
          <Form.Item
            label="Full name"
            validateStatus={errors.fullName ? 'error' : undefined}
            help={errors.fullName?.message}
          >
            <Controller
              name="fullName"
              control={control}
              render={({ field }) => <Input {...field} maxLength={100} />}
            />
          </Form.Item>
          <Form.Item
            label="Email"
            validateStatus={errors.email ? 'error' : undefined}
            help={errors.email?.message}
          >
            <Controller
              name="email"
              control={control}
              render={({ field }) => <Input {...field} maxLength={255} type="email" />}
            />
          </Form.Item>
          <Form.Item
            label="Roles"
            validateStatus={errors.roles ? 'error' : undefined}
            help={errors.roles?.message}
          >
            <Controller
              name="roles"
              control={control}
              render={({ field }) => (
                <Select<RoleName[]>
                  {...field}
                  mode="multiple"
                  options={[
                    { value: 'USER', label: 'User' },
                    { value: 'ADMIN', label: 'Administrator' },
                  ]}
                />
              )}
            />
          </Form.Item>
          <Form.Item
            label="Initial password"
            validateStatus={errors.password ? 'error' : undefined}
            help={errors.password?.message}
          >
            <Controller
              name="password"
              control={control}
              render={({ field }) => <Input.Password {...field} autoComplete="new-password" />}
            />
          </Form.Item>
          <Form.Item
            label="Confirm password"
            validateStatus={errors.confirmPassword ? 'error' : undefined}
            help={errors.confirmPassword?.message}
          >
            <Controller
              name="confirmPassword"
              control={control}
              render={({ field }) => <Input.Password {...field} autoComplete="new-password" />}
            />
          </Form.Item>
        </form>
      </Form>
    </Drawer>
  );
}
