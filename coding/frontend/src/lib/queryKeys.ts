export const queryKeys = {
  auth: {
    me: ['auth', 'me'] as const,
  },
  projects: {
    all: ['projects'] as const,
    list: (filters: object) => ['projects', 'list', filters] as const,
    detail: (projectId: string) => ['projects', 'detail', projectId] as const,
    members: (projectId: string, page = 0) => ['projects', projectId, 'members', page] as const,
  },
  tasks: {
    list: (projectId: string, filters: object) =>
      ['projects', projectId, 'tasks', filters] as const,
    detail: (projectId: string, taskId: string) =>
      ['projects', projectId, 'tasks', taskId] as const,
  },
  timeline: {
    aggregate: (projectId: string) => ['projects', projectId, 'timeline'] as const,
    versions: (projectId: string, page: number) =>
      ['projects', projectId, 'timeline', 'versions', page] as const,
  },
  users: {
    list: (filters: object) => ['users', 'list', filters] as const,
    roles: ['users', 'roles'] as const,
  },
  holidays: {
    list: (year: number | null, page: number) => ['holidays', year, page] as const,
  },
  dashboard: {
    all: ['dashboard'] as const,
  },
};
