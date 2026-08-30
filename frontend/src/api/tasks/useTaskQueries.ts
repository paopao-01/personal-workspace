import { useQuery } from '@tanstack/react-query'
import { getTask, listTasks, type LearningTask, type PageTask, type TaskListParams } from '@/api/tasks/taskApi'

export function useTasks(params: TaskListParams) {
  return useQuery<PageTask>({
    queryKey: ['tasks', params],
    queryFn: () => listTasks(params),
  })
}

export function useTask(taskId: string | undefined) {
  return useQuery<LearningTask>({
    queryKey: ['tasks', taskId],
    queryFn: () => getTask(taskId!),
    enabled: Boolean(taskId),
  })
}
