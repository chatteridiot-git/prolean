import { Progress, Space, Typography } from 'antd';
import { formatPercent, progressTone } from '@/lib/progress';

export function PercentBar({
  percent,
  status = 'IN_PROGRESS',
  label,
}: {
  percent: number;
  status?: string;
  label?: string;
}) {
  const clamped = Math.max(0, Math.min(100, percent));
  const tone = progressTone(status, percent);
  return (
    <Space direction="vertical" size={0} className="percent-bar">
      {label ? <Typography.Text type="secondary">{label}</Typography.Text> : null}
      <Space>
        <Progress
          percent={clamped}
          size="small"
          status={tone === 'active' ? 'active' : tone}
          showInfo={false}
          className="percent-progress"
        />
        <Typography.Text strong>{formatPercent(clamped)}</Typography.Text>
      </Space>
    </Space>
  );
}
