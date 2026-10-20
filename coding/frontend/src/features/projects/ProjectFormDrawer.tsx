import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, DatePicker, Drawer, Form, Input, Select, Space } from 'antd';
import dayjs from 'dayjs';
import { useAuth } from '@/auth/AuthProvider';
import { useUsers } from '@/features/users/api';
import { problemMessage } from '@/lib/errors';
import { toApiDate } from '@/lib/dates';
import { projectSchema } from '@/lib/validation';
import type { ProjectValues } from '@/lib/validation';
import type { Project } from '@/types/api';

export function ProjectFormDrawer({
  open,
  project,
  submitting,
  onClose,
  onSubmit,
}: {
  open: boolean;
  project?: Project | null;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (values: ProjectValues) => Promise<void>;
}) {
  const { isAdmin } = useAuth();
  const [error, setError] = useState<unknown>(null);
  const usersQuery = useUsers({ page: 0, size: 100, enabled: true, sort: 'fullName,asc' });
  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ProjectValues>({
    resolver: zodResolver(projectSchema),
    defaultValues: {
      code: '',
      name: '',
      description: '',
      startDate: dayjs().format('YYYY-MM-DD'),
      endDate: dayjs().add(30, 'day').format('YYYY-MM-DD'),
      memberIds: [],
    },
  });

  useEffect(() => {
    if (!open) return;
    setError(null);
    reset({
      code: project?.code ?? '',
      name: project?.name ?? '',
      description: project?.description ?? '',
      startDate: project?.startDate ?? dayjs().format('YYYY-MM-DD'),
      endDate: project?.endDate ?? dayjs().add(30, 'day').format('YYYY-MM-DD'),
      memberIds: [],
    });
  }, [open, project, reset]);

  const submit = async (values: ProjectValues) => {
    setError(null);
    try {
      await onSubmit(values);
    } catch (cause) {
      setError(cause);
    }
  };

  return (
    <Drawer
      title={project ? 'Edit project' : 'Create project'}
      open={open}
      onClose={onClose}
      width={520}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" htmlType="submit" form="project-form" loading={submitting}>
            {project ? 'Save changes' : 'Create project'}
          </Button>
        </Space>
      }
    >
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
        <form id="project-form" onSubmit={handleSubmit(submit)} noValidate>
          <Form.Item
            label="Project code"
            validateStatus={errors.code ? 'error' : undefined}
            help={errors.code?.message}
          >
            <Controller
              name="code"
              control={control}
              render={({ field }) => (
                <Input
                  {...field}
                  placeholder="NORTH-STAR"
                  disabled={Boolean(project)}
                  maxLength={30}
                  onChange={(event) => field.onChange(event.target.value.toUpperCase())}
                />
              )}
            />
          </Form.Item>
          <Form.Item
            label="Name"
            validateStatus={errors.name ? 'error' : undefined}
            help={errors.name?.message}
          >
            <Controller
              name="name"
              control={control}
              render={({ field }) => <Input {...field} placeholder="Project name" maxLength={150} />}
            />
          </Form.Item>
          <Form.Item
            label="Description"
            validateStatus={errors.description ? 'error' : undefined}
            help={errors.description?.message}
          >
            <Controller
              name="description"
              control={control}
              render={({ field }) => (
                <Input.TextArea {...field} rows={3} maxLength={2000} showCount />
              )}
            />
          </Form.Item>
          <Space size="middle" className="full-width" align="start">
            <Form.Item
              label="Start date"
              validateStatus={errors.startDate ? 'error' : undefined}
              help={errors.startDate?.message}
              className="half-width"
            >
              <Controller
                name="startDate"
                control={control}
                render={({ field }) => (
                  <DatePicker
                    value={field.value ? dayjs(field.value) : null}
                    onChange={(value) => field.onChange(toApiDate(value))}
                    format="DD MMM YYYY"
                    className="full-width"
                  />
                )}
              />
            </Form.Item>
            <Form.Item
              label="End date"
              validateStatus={errors.endDate ? 'error' : undefined}
              help={errors.endDate?.message}
              className="half-width"
            >
              <Controller
                name="endDate"
                control={control}
                render={({ field }) => (
                  <DatePicker
                    value={field.value ? dayjs(field.value) : null}
                    onChange={(value) => field.onChange(toApiDate(value))}
                    format="DD MMM YYYY"
                    className="full-width"
                  />
                )}
              />
            </Form.Item>
          </Space>
          {!project && isAdmin ? (
            <Form.Item
              label="Initial members"
              validateStatus={errors.memberIds ? 'error' : undefined}
              help={errors.memberIds?.message}
            >
              <Controller
                name="memberIds"
                control={control}
                render={({ field }) => (
                  <Select
                    {...field}
                    mode="multiple"
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    loading={usersQuery.isLoading}
                    placeholder="Select active users"
                    options={(usersQuery.data?.content ?? [])
                      .filter((member) => member.enabled)
                      .map((member) => ({
                        value: member.id,
                        label: `${member.fullName} (${member.username})`,
                      }))}
                  />
                )}
              />
            </Form.Item>
          ) : null}
        </form>
      </Form>
    </Drawer>
  );
}
