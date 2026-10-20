import { Tag } from 'antd';
import type {
  HolidayType,
  LifecycleStatus,
  OperationalStatus,
  ProjectStatus,
  TaskStatus,
} from '@/types/api';

const labels: Record<string, string> = {
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  CLOSED: 'Closed',
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  SUPERSEDED: 'Superseded',
  CANCELLED: 'Cancelled',
  NATIONAL: 'National holiday',
  JOINT_LEAVE: 'Joint leave',
};

const colors: Record<string, string> = {
  ACTIVE: 'green',
  ARCHIVED: 'default',
  OPEN: 'blue',
  IN_PROGRESS: 'gold',
  CLOSED: 'green',
  DRAFT: 'orange',
  PUBLISHED: 'green',
  SUPERSEDED: 'default',
  CANCELLED: 'red',
  NATIONAL: 'purple',
  JOINT_LEAVE: 'cyan',
};

export function StatusTag({
  value,
}: {
  value: ProjectStatus | TaskStatus | LifecycleStatus | OperationalStatus | HolidayType;
}) {
  return <Tag color={colors[value]}>{labels[value] ?? value}</Tag>;
}
