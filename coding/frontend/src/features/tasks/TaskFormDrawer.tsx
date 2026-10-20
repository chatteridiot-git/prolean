import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Button,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
} from 'antd';
import dayjs from 'dayjs';
import { problemMessage } from '@/lib/errors';
import { toApiDate } from '@/lib/dates';
import { taskSchema } from '@/lib/validation';
import type { TaskValues } from '@/lib/validation';
import type { ProjectMember, Task } from '@/types/api';

export function TaskFormDrawer({
  open,
  task,
  members,
  submitting,
  onClose,
  onSubmit,
}: {
  open: boolean;
  task?: Task | null;
  members: ProjectMember[];
  submitting: boolean;
  onClose: () => void;
  onSubmit: (values: TaskValues) => Promise<void>;
}) {
  const [error, setError] = useState<unknown>(null);
  const {
    control,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<TaskValues>({
    resolver: zodResolver(taskSchema),
    defaultValues: {
      title: '',
      description: '',
      startDate: dayjs().format('YYYY-MM-DD'),
      endDate: dayjs().add(3, 'day').format('YYYY-MM-DD'),
      picUserId: members[0]?.userId ?? '',
      status: 'OPEN',
      percentComplete: 0,
    },
  });
  const status = watch('status');

  useEffect(() => {
    if (!open) return;
    setError(null);
    reset({
      title: task?.title ?? '',
      description: task?.description ?? '',
      startDate: task?.startDate ?? dayjs().format('YYYY-MM-DD'),
      endDate: task?.endDate ?? dayjs().add(3, 'day').format('YYYY-MM-DD'),
      picUserId: task?.pic.userId ?? members[0]?.userId ?? '',
      status: task?.status ?? 'OPEN',
      percentComplete: task?.percentComplete ?? 0,
    });
  }, [open, task, members, reset]);

  useEffect(() => {
    if (status === 'CLOSED') setValue('percentComplete', 100);
  }, [status, setValue]);

  const submit = async (values: TaskValues) => {
    setError(null);
    try {
      await onSubmit(values);
    } catch (cause) {
      setError(cause);
    }
  };

  return (
    <Drawer
      title={task ? 'Edit task' : 'Create task'}
      open={open}
      onClose={onClose}
      width={560}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" htmlType="submit" form="task-form" loading={submitting}>
            {task ? 'Save changes' : 'Create task'}
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
        <form id="task-form" onSubmit={handleSubmit(submit)} noValidate>
          <Form.Item
            label="Title"
            validateStatus={errors.title ? 'error' : undefined}
            help={errors.title?.message}
          >
            <Controller
              name="title"
              control={control}
              render={({ field }) => <Input {...field} maxLength={200} placeholder="Task title" />}
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
          <Form.Item
            label="Person in charge"
            validateStatus={errors.picUserId ? 'error' : undefined}
            help={errors.picUserId?.message}
          >
            <Controller
              name="picUserId"
              control={control}
              render={({ field }) => (
                <Select
                  {...field}
                  showSearch
                  optionFilterProp="label"
                  placeholder="Select a project member"
                  options={members.map((member) => ({
                    value: member.userId,
                    label: `${member.fullName} (${member.username})`,
                  }))}
                />
              )}
            />
          </Form.Item>
          <Space size="middle" className="full-width" align="start">
            <Form.Item
              label="Status"
              validateStatus={errors.status ? 'error' : undefined}
              help={errors.status?.message}
              className="half-width"
            >
              <Controller
                name="status"
                control={control}
                render={({ field }) => (
                  <Select
                    {...field}
                    options={[
                      { value: 'OPEN', label: 'Open' },
                      { value: 'IN_PROGRESS', label: 'In progress' },
                      { value: 'CLOSED', label: 'Closed' },
                    ]}
                  />
                )}
              />
            </Form.Item>
            <Form.Item
              label="Percent complete"
              validateStatus={errors.percentComplete ? 'error' : undefined}
              help={errors.percentComplete?.message}
              className="half-width"
            >
              <Controller
                name="percentComplete"
                control={control}
                render={({ field }) => (
                  <InputNumber
                    {...field}
                    min={0}
                    max={100}
                    disabled={status === 'CLOSED'}
                    addonAfter="%"
                    className="full-width"
                  />
                )}
              />
            </Form.Item>
          </Space>
        </form>
      </Form>
    </Drawer>
  );
}
