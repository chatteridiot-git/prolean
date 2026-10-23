export const statusLabels: Record<string, string> = {
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

export const statusColors: Record<string, string> = {
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

export function statusLabel(value: string): string {
  return statusLabels[value] ?? value;
}

export function statusColor(value: string): string {
  return statusColors[value] ?? 'default';
}
