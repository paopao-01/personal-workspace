import { useState, type FormEvent } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import { useCreateTask, useTransitionTask } from '@/api/tasks/useTaskMutations'
import { useTasks } from '@/api/tasks/useTaskQueries'
import type { LearningTask, TaskStatus } from '@/api/tasks/taskApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select, Textarea } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { taskStatusLabel, taskStatusVariant } from '@/features/tasks/taskLabels'

const nextActions: Record<TaskStatus, Array<{ target: TaskStatus; label: string }>> = {
  TODO: [
    { target: 'IN_PROGRESS', label: '开始' },
    { target: 'ABANDONED', label: '放弃' },
  ],
  IN_PROGRESS: [
    { target: 'COMPLETED', label: '完成' },
    { target: 'TODO', label: '重置' },
    { target: 'ABANDONED', label: '放弃' },
  ],
  COMPLETED: [{ target: 'IN_PROGRESS', label: '重新打开' }],
  ABANDONED: [{ target: 'TODO', label: '恢复' }],
}

export function TaskListPage() {
  const [status, setStatus] = useState<TaskStatus | ''>('')
  const [title, setTitle] = useState('')
  const [acceptanceCriteria, setAcceptanceCriteria] = useState('')
  const [verificationMethod, setVerificationMethod] = useState('')
  const [verificationDrafts, setVerificationDrafts] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const tasksQuery = useTasks({ page: 1, pageSize: 50, status: status || undefined })
  const createTask = useCreateTask()
  const transitionTask = useTransitionTask()

  const pending = createTask.isPending || transitionTask.isPending

  if (tasksQuery.isLoading) {
    return <Spinner label="加载学习任务…" />
  }
  if (tasksQuery.error) {
    return <ErrorState error={tasksQuery.error} onRetry={() => tasksQuery.refetch()} />
  }

  const reportError = (caught: Error) => {
    const message =
      isApiError(caught) || isNetworkError(caught)
        ? caught.message
        : '任务操作失败，请稍后重试'
    setError(message)
    pushToast(message, 'error')
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      await createTask.mutateAsync({
        title,
        priority: 'MEDIUM',
        acceptanceCriteria: acceptanceCriteria.trim() || undefined,
        verificationMethod: verificationMethod.trim() || undefined,
      })
      setTitle('')
      setAcceptanceCriteria('')
      setVerificationMethod('')
      pushToast('学习任务已创建')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const transition = async (task: LearningTask, targetStatus: TaskStatus) => {
    setError(null)
    try {
      await transitionTask.mutateAsync({
        taskId: task.id,
        version: task.version,
        body: {
          targetStatus,
          verificationResult:
            targetStatus === 'COMPLETED'
              ? verificationDrafts[task.id]?.trim() || undefined
              : undefined,
        },
      })
      pushToast('任务状态已更新')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const tasks = tasksQuery.data?.items ?? []

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">学习任务</h1>
          <p className="page-subtitle">把薄弱问题变成可验证的行动，任务完成不会自动修改能力等级。</p>
        </div>
      </div>

      {error ? (
        <div className="conflict-banner">
          <span>{error}</span>
        </div>
      ) : null}

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">新建任务</h2>
        </div>
        <div className="card-body">
          <form onSubmit={submit} noValidate>
            <Field label="任务名称" required>
              <Input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                maxLength={200}
                required
              />
            </Field>
            <div className="form-row">
              <Field label="验收标准">
                <Textarea
                  value={acceptanceCriteria}
                  onChange={(event) => setAcceptanceCriteria(event.target.value)}
                  rows={3}
                  maxLength={5000}
                />
              </Field>
              <Field label="验证方式">
                <Textarea
                  value={verificationMethod}
                  onChange={(event) => setVerificationMethod(event.target.value)}
                  rows={3}
                  maxLength={1000}
                />
              </Field>
            </div>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              <Button variant="primary" type="submit" disabled={pending || !title.trim()}>
                {createTask.isPending ? '创建中…' : '创建任务'}
              </Button>
            </div>
          </form>
        </div>
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">任务列表</h2>
          <div style={{ width: 180 }}>
            <Select
              value={status}
              onChange={(event) => setStatus(event.target.value as TaskStatus | '')}
              aria-label="按任务状态筛选"
            >
              <option value="">全部状态</option>
              <option value="TODO">{taskStatusLabel.TODO}</option>
              <option value="IN_PROGRESS">{taskStatusLabel.IN_PROGRESS}</option>
              <option value="COMPLETED">{taskStatusLabel.COMPLETED}</option>
              <option value="ABANDONED">{taskStatusLabel.ABANDONED}</option>
            </Select>
          </div>
        </div>
        <div className="card-body">
          {tasks.length === 0 ? (
            <EmptyState icon="□" text="暂无学习任务" />
          ) : (
            <div>
              {tasks.map((task) => (
                <div className="requirement-row" key={task.id}>
                  <div className="requirement-main">
                    <span className="requirement-raw">{task.title}</span>
                    <div className="requirement-meta">
                      <Badge variant={taskStatusVariant[task.status]}>
                        {taskStatusLabel[task.status]}
                      </Badge>
                      {task.knowledgePoints?.map((point) => (
                        <Badge variant="subtle" key={point.id}>
                          {point.name}
                        </Badge>
                      ))}
                    </div>
                    {task.acceptanceCriteria ? (
                      <span className="muted">验收：{task.acceptanceCriteria}</span>
                    ) : null}
                    {task.verificationMethod ? (
                      <span className="muted">验证方式：{task.verificationMethod}</span>
                    ) : null}
                    {task.verificationResult ? (
                      <span className="muted">验证结果：{task.verificationResult}</span>
                    ) : null}
                    {task.status === 'IN_PROGRESS' ? (
                      <Field label="完成验证结果">
                        <Input
                          value={verificationDrafts[task.id] ?? ''}
                          onChange={(event) =>
                            setVerificationDrafts((prev) => ({
                              ...prev,
                              [task.id]: event.target.value,
                            }))
                          }
                          maxLength={5000}
                          placeholder="例如 已完成自测并记录卡点"
                        />
                      </Field>
                    ) : null}
                  </div>
                  <div className="requirement-actions">
                    {nextActions[task.status].map((action) => (
                      <Button
                        key={action.target}
                        size="sm"
                        variant={action.target === 'COMPLETED' ? 'primary' : 'default'}
                        type="button"
                        disabled={pending}
                        onClick={() => transition(task, action.target)}
                      >
                        {action.label}
                      </Button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
