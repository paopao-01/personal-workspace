import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { isApiError, isNetworkError } from '@/api/errors'
import { useCreateTask, useTransitionTask, useUpdateTask } from '@/api/tasks/useTaskMutations'
import { useTasks } from '@/api/tasks/useTaskQueries'
import { useKnowledgePoints } from '@/api/reviews/useReviewQueries'
import type { LearningTask, TaskPriority, TaskSourceType, TaskStatus } from '@/api/tasks/taskApi'
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
	const [sourceType, setSourceType] = useState<TaskSourceType | ''>('')
  const [dueBefore, setDueBefore] = useState('')
  const [jobId, setJobId] = useState('')
  const [interviewId, setInterviewId] = useState('')
  const [knowledgePointId, setKnowledgePointId] = useState('')
  const [title, setTitle] = useState('')
  const [type, setType] = useState('')
  const [estimatedMinutes, setEstimatedMinutes] = useState('')
  const [learningGoal, setLearningGoal] = useState('')
  const [outputUrl, setOutputUrl] = useState('')
  const [selectedKnowledgePointIds, setSelectedKnowledgePointIds] = useState<string[]>([])
  const [relatedQuestionIds, setRelatedQuestionIds] = useState('')
  const [acceptanceCriteria, setAcceptanceCriteria] = useState('')
  const [verificationMethod, setVerificationMethod] = useState('')
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM')
  const [dueAt, setDueAt] = useState('')
  const [relatedJobIds, setRelatedJobIds] = useState('')
  const [verificationDrafts, setVerificationDrafts] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
	const tasksQuery = useTasks({ page: 1, pageSize: 50, status: status || undefined, sourceType: sourceType || undefined, dueBefore: dueBefore || undefined, jobId: jobId || undefined, interviewId: interviewId || undefined, knowledgePointId: knowledgePointId || undefined })
	const knowledgePointsQuery = useKnowledgePoints('')
  const createTask = useCreateTask()
  const transitionTask = useTransitionTask()
  const updateTask = useUpdateTask()

  const pending = createTask.isPending || transitionTask.isPending || updateTask.isPending

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
        type: type.trim() || undefined,
        priority,
        estimatedMinutes: estimatedMinutes ? Number(estimatedMinutes) : undefined,
        dueAt: dueAt || undefined,
        learningGoal: learningGoal.trim() || undefined,
        relatedJobIds: relatedJobIds.split(',').map((id) => id.trim()).filter(Boolean),
        relatedQuestionIds: relatedQuestionIds.split(',').map((id) => id.trim()).filter(Boolean),
        knowledgePointIds: selectedKnowledgePointIds,
        acceptanceCriteria: acceptanceCriteria.trim() || undefined,
        verificationMethod: verificationMethod.trim() || undefined,
        outputUrl: outputUrl.trim() || undefined,
      })
      setTitle('')
      setType('')
      setEstimatedMinutes('')
      setLearningGoal('')
      setAcceptanceCriteria('')
      setVerificationMethod('')
      setOutputUrl('')
      setPriority('MEDIUM')
      setDueAt('')
      setRelatedJobIds('')
      setRelatedQuestionIds('')
      setSelectedKnowledgePointIds([])
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
              <Field label="任务类型"><Input value={type} onChange={(event) => setType(event.target.value)} maxLength={100} placeholder="如：知识点巩固" /></Field>
              <Field label="预计耗时（分钟）"><Input type="number" min={1} value={estimatedMinutes} onChange={(event) => setEstimatedMinutes(event.target.value)} /></Field>
            </div>
            <div className="form-row">
              <Field label="优先级">
                <Select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}>
                  <option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option><option value="URGENT">紧急</option>
                </Select>
              </Field>
              <Field label="截止时间">
                <Input type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} />
              </Field>
              <Field label="关联岗位 ID" hint="多个 ID 用逗号分隔">
                <Input value={relatedJobIds} onChange={(event) => setRelatedJobIds(event.target.value)} maxLength={5000} />
              </Field>
              <Field label="关联面试问题 ID" hint="多个 ID 用逗号分隔">
                <Input value={relatedQuestionIds} onChange={(event) => setRelatedQuestionIds(event.target.value)} maxLength={5000} />
              </Field>
            </div>
            <Field label="关联知识点">
              {knowledgePointsQuery.data?.length ? (
                <div className="evidence-picker" role="group" aria-label="关联知识点">
                  {knowledgePointsQuery.data.map((point) => <label key={point.id} className="evidence-picker-item"><input type="checkbox" checked={selectedKnowledgePointIds.includes(point.id)} onChange={() => setSelectedKnowledgePointIds((prev) => prev.includes(point.id) ? prev.filter((id) => id !== point.id) : [...prev, point.id])} />{point.name}</label>)}
                </div>
              ) : <span className="form-hint">暂无知识点，可从复盘问题中创建。</span>}
            </Field>
            <Field label="学习目标"><Textarea rows={3} value={learningGoal} onChange={(event) => setLearningGoal(event.target.value)} maxLength={5000} /></Field>
            <div className="form-row">
              <Field label="验收标准">
                <Textarea
                  value={acceptanceCriteria}
                  onChange={(event) => setAcceptanceCriteria(event.target.value)}
                  rows={3}
                  maxLength={5000}
                />
              </Field>
              <Field label="产出物链接"><Input value={outputUrl} onChange={(event) => setOutputUrl(event.target.value)} maxLength={2000} /></Field>
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
          <div className="flex-row" style={{ gap: 8 }}>
          <div style={{ width: 160 }}>
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
          <div style={{ width: 150 }}>
			<Select value={sourceType} onChange={(event) => setSourceType(event.target.value as TaskSourceType | '')} aria-label="按来源筛选">
              <option value="">全部来源</option><option value="QUESTION">面试问题</option><option value="JOB">岗位</option><option value="KNOWLEDGE_POINT">知识点</option><option value="MANUAL">手工</option>
            </Select>
          </div>
          <Input type="datetime-local" value={dueBefore} onChange={(event) => setDueBefore(event.target.value)} aria-label="截止时间筛选" />
          <Input value={jobId} onChange={(event) => setJobId(event.target.value)} placeholder="关联岗位 ID" aria-label="关联岗位筛选" />
          <Input value={interviewId} onChange={(event) => setInterviewId(event.target.value)} placeholder="关联面试 ID" aria-label="关联面试筛选" />
          <Select value={knowledgePointId} onChange={(event) => setKnowledgePointId(event.target.value)} aria-label="按知识点筛选">
            <option value="">全部知识点</option>
            {(knowledgePointsQuery.data ?? []).map((point) => <option key={point.id} value={point.id}>{point.name}</option>)}
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
                    <Link className="requirement-raw" to={`/tasks/${task.id}`}>{task.title}</Link>
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
                    <span className="muted">{task.dueAt ? `截止：${task.dueAt}` : '无截止时间'} · 优先级：{task.priority ?? 'MEDIUM'}</span>
                    {task.sourceRefs?.length ? <span className="muted">来源：{task.sourceRefs.map((source) => source.label).join('、')}</span> : null}
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
