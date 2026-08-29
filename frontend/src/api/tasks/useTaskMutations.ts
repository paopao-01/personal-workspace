import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createTask,
  createTaskFromQuestion,
  transitionTask,
  updateTask,
  type CreateTaskFromQuestionRequest,
  type LearningTask,
  type TaskCreateRequest,
  type TaskTransitionRequest,
  type TaskUpdateRequest,
} from '@/api/tasks/taskApi'

export function useCreateTask() {
  const queryClient = useQueryClient()
  return useMutation<LearningTask, Error, TaskCreateRequest>({
    mutationFn: createTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: ['knowledge-points', 'weak'] })
    },
  })
}

export function useUpdateTask() {
  const queryClient = useQueryClient()
  return useMutation<
    LearningTask,
    Error,
    { taskId: string; version: number; body: TaskUpdateRequest }
  >({
    mutationFn: ({ taskId, version, body }) => updateTask(taskId, version, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
    },
  })
}

export function useTransitionTask() {
  const queryClient = useQueryClient()
  return useMutation<
    LearningTask,
    Error,
    { taskId: string; version: number; body: TaskTransitionRequest }
  >({
    mutationFn: ({ taskId, version, body }) => transitionTask(taskId, version, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: ['knowledge-points', 'weak'] })
    },
  })
}

export function useCreateTaskFromQuestion() {
  const queryClient = useQueryClient()
  return useMutation<
    LearningTask,
    Error,
    { questionId: string; body: CreateTaskFromQuestionRequest }
  >({
    mutationFn: ({ questionId, body }) => createTaskFromQuestion(questionId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: ['knowledge-points', 'weak'] })
    },
  })
}
