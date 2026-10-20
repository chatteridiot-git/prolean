import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, DatePicker, Drawer, Form, Input, Space } from 'antd';
import dayjs from 'dayjs';
import { problemMessage } from '@/lib/errors';
import { toApiDate } from '@/lib/dates';
import { timelineSchema } from '@/lib/validation';
import type { TimelineValues } from '@/lib/validation';
import type { TimelineVersion } from '@/types/api';

export type TimelineFormMode = 'create' | 'edit' | 'revise';

const titles: Record<TimelineFormMode, string> = {
  create: 'Create timeline',
  edit: 'Edit draft timeline',
  revise: 'Start a new revision',
};

const submitLabels: Record<TimelineFormMode, string> = {
  create: 'Create draft',
  edit: 'Save draft',
  revise: 'Create draft revision',
};

export function TimelineFormDrawer({
  open,
  mode,
  version,
  submitting,
  onClose,
  onSubmit,
}: {
  open: boolean;
  mode: TimelineFormMode;
  version?: TimelineVersion | null;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (values: TimelineValues) => Promise<void>;
}) {
  const [error, setError] = useState<unknown>(null);
  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<TimelineValues>({
    resolver: zodResolver(timelineSchema),
    defaultValues: {
      startDate: dayjs().format('YYYY-MM-DD'),
      endDate: dayjs().add(30, 'day').format('YYYY-MM-DD'),
      revisionNotes: '',
    },
  });

  useEffect(() => {
    if (!open) return;
    setError(null);
    reset({
      startDate: version?.startDate ?? dayjs().format('YYYY-MM-DD'),
      endDate: version?.endDate ?? dayjs().add(30, 'day').format('YYYY-MM-DD'),
      revisionNotes: mode === 'edit' ? version?.revisionNotes ?? '' : '',
    });
  }, [open, mode, version, reset]);

  const submit = async (values: TimelineValues) => {
    setError(null);
    try {
      await onSubmit({
        ...values,
        revisionNotes: values.revisionNotes?.trim() ? values.revisionNotes : undefined,
      });
    } catch (cause) {
      setError(cause);
    }
  };

  return (
    <Drawer
      title={titles[mode]}
      open={open}
      onClose={onClose}
      width={520}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" htmlType="submit" form="timeline-form" loading={submitting}>
            {submitLabels[mode]}
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
      <Alert
        type="info"
        showIcon
        message="Working days exclude weekends and Indonesian national holidays and joint leave."
        className="section-gap"
      />
      <Form component={false} layout="vertical" requiredMark={false}>
        <form id="timeline-form" onSubmit={handleSubmit(submit)} noValidate>
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
            label={mode === 'revise' ? 'Revision notes (optional)' : 'Draft notes'}
            validateStatus={errors.revisionNotes ? 'error' : undefined}
            help={errors.revisionNotes?.message}
          >
            <Controller
              name="revisionNotes"
              control={control}
              render={({ field }) => (
                <Input.TextArea
                  {...field}
                  rows={4}
                  maxLength={1000}
                  showCount
                  placeholder={
                    mode === 'revise'
                      ? 'What is changing in this revision?'
                      : 'Context for the next reviewer'
                  }
                />
              )}
            />
          </Form.Item>
        </form>
      </Form>
    </Drawer>
  );
}
