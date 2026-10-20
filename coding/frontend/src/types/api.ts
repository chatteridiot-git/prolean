export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'CLOSED';
export type LifecycleStatus = 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED' | 'CANCELLED';
export type OperationalStatus = 'OPEN' | 'IN_PROGRESS' | 'CLOSED';
export type HolidayType = 'NATIONAL' | 'JOINT_LEAVE';
export type RoleName = 'ADMIN' | 'USER';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface UserSummary {
  id: string;
  username: string;
  fullName: string;
  roles: RoleName[];
}

export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  enabled: boolean;
  roles: RoleName[];
  createdAt: string;
  updatedAt: string;
}

export interface Role {
  id: string;
  name: RoleName;
  description: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export interface RefreshResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface ProjectSummary {
  id: string;
  code: string;
  name: string;
  status: ProjectStatus;
  startDate: string;
  endDate: string;
  memberCount: number;
}

export interface Project {
  id: string;
  code: string;
  name: string;
  description?: string;
  startDate: string;
  endDate: string;
  status: ProjectStatus;
  memberCount: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectMember {
  userId: string;
  username: string;
  fullName: string;
  email: string;
  addedAt: string;
}

export interface MembersAddedResponse {
  added: number;
  alreadyMembers: number;
}

export interface Pic {
  userId: string;
  name: string;
}

export interface TaskSummary {
  id: string;
  projectId: string;
  title: string;
  startDate: string;
  endDate: string;
  durationDays: number;
  pic: Pic;
  status: TaskStatus;
  percentComplete: number;
}

export interface Task extends TaskSummary {
  description?: string;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface TimelineVersion {
  id: string;
  timelineId: string;
  projectId: string;
  versionNumber: number;
  lifecycleStatus: LifecycleStatus;
  startDate: string;
  endDate: string;
  durationWorkdays: number;
  operationalStatus: OperationalStatus;
  percentActual: number;
  percentPlan: number;
  planAsOfDate: string;
  planWarning?: 'NO_WORKING_DAYS' | null;
  revisionNotes?: string;
  createdBy: string;
  createdAt: string;
  updatedBy?: string;
  updatedAt: string;
  publishedBy?: string;
  publishedAt?: string;
  supersededBy?: string;
  supersededAt?: string;
  snapshotDurationWorkdays?: number;
  snapshotPercentPlan?: number;
  snapshotPercentActual?: number;
  snapshotAsOfDate?: string;
  cancelledBy?: string;
  cancelledAt?: string;
}

export interface TimelineAggregate {
  timelineId: string;
  projectId: string;
  versionCount: number;
  current: TimelineVersion | null;
  draft: TimelineVersion | null;
}

export interface Holiday {
  id: string;
  date: string;
  name: string;
  type: HolidayType;
  createdBy: string;
  updatedBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  requestId?: string;
  timestamp?: string;
  errors?: { field: string; message: string }[];
}
