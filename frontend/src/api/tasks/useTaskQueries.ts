import { useQuery } from '@tanstack/react-query'
import { listTasks, type PageTask, type TaskListParams } from '@/api/tasks/taskApi'

export function useTasks(params: TaskListParams) {
  return useQuery<PageTask>({
    queryKey: ['tasks', params],
    queryFn: () => listTasks(params),
  })
}
