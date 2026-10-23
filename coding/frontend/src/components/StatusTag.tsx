import { Tag } from 'antd';
import { statusColor, statusLabel } from '@/lib/status';
import type {
  HolidayType,
  LifecycleStatus,
  OperationalStatus,
  ProjectStatus,
  TaskStatus,
} from '@/types/api';

export function StatusTag({
  value,
}: {
  value: ProjectStatus | TaskStatus | LifecycleStatus | OperationalStatus | HolidayType;
}) {
  return <Tag color={statusColor(value)}>{statusLabel(value)}</Tag>;
}
