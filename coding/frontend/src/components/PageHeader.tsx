import type { ReactNode } from 'react';
import { Space, Typography } from 'antd';

export function PageHeader({
  title,
  subtitle,
  extra,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  extra?: ReactNode;
}) {
  return (
    <div className="page-header">
      <div>
        <Typography.Title level={3} className="page-title">
          {title}
        </Typography.Title>
        {subtitle ? (
          <Typography.Paragraph type="secondary" className="page-subtitle">
            {subtitle}
          </Typography.Paragraph>
        ) : null}
      </div>
      {extra ? <Space wrap>{extra}</Space> : null}
    </div>
  );
}
