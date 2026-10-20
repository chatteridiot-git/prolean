import type { ReactNode } from 'react';
import { Alert, Button, Empty, Skeleton, Space, Typography } from 'antd';
import { problemMessage, problemFrom } from '@/lib/errors';

export function ProblemAlert({
  error,
  title = 'Unable to load data',
  action,
}: {
  error: unknown;
  title?: string;
  action?: ReactNode;
}) {
  const problem = problemFrom(error);
  return (
    <Alert
      type="error"
      showIcon
      message={title}
      description={
        <Space direction="vertical" size={2}>
          <span>{problemMessage(error)}</span>
          {problem.requestId ? (
            <Typography.Text type="secondary" className="small-text">
              Request ID: {problem.requestId}
            </Typography.Text>
          ) : null}
        </Space>
      }
      action={action}
    />
  );
}

export function AsyncState({
  loading,
  error,
  empty,
  emptyText = 'Nothing here yet.',
  onRetry,
  children,
}: {
  loading: boolean;
  error?: unknown;
  empty?: boolean;
  emptyText?: string;
  onRetry?: () => void;
  children: ReactNode;
}) {
  if (loading) return <Skeleton active paragraph={{ rows: 5 }} />;
  if (error) {
    return (
      <ProblemAlert
        error={error}
        action={
          onRetry ? (
            <Button size="small" onClick={onRetry}>
              Retry
            </Button>
          ) : undefined
        }
      />
    );
  }
  if (empty) return <Empty description={emptyText} />;
  return <>{children}</>;
}
