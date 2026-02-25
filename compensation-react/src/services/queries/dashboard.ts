import { useQuery } from '@tanstack/react-query';
import api, { unwrap } from '@services/api';
import { qk } from '@types/api';
import type { DashboardMetrics, SystemStatus, TodoItem, ActivityItem } from '@types/api';

export function useDashboardMetricsQuery() {
  return useQuery({
    queryKey: qk.dashboardMetrics,
    queryFn: async () => {
      const { data } = await api.get('/dashboard/metrics');
      return unwrap<DashboardMetrics>(data);
    },
  });
}

export function useDashboardStatusQuery() {
  return useQuery({
    queryKey: qk.dashboardStatus,
    queryFn: async () => {
      const { data } = await api.get('/dashboard/status');
      return unwrap<SystemStatus>(data);
    },
  });
}

export function useDashboardTodosQuery() {
  return useQuery({
    queryKey: qk.dashboardTodos,
    queryFn: async () => {
      const { data } = await api.get('/dashboard/todos');
      return unwrap<TodoItem[]>(data);
    },
  });
}

export function useDashboardActivitiesQuery() {
  return useQuery({
    queryKey: qk.dashboardActivities,
    queryFn: async () => {
      const { data } = await api.get('/dashboard/activities');
      return unwrap<ActivityItem[]>(data);
    },
  });
}
