import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { isApiError, isNetworkError } from '@/api/errors'
import { useTask } from '@/api/tasks/useTaskQueries'
import { useTransitionTask, useUpdateTask } from '@/api/tasks/useTaskMutations'
import type { LearningTask, TaskPriority, TaskStatus, TaskUpdateRequest } from '@/api/tasks/taskApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select, Textarea } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { taskStatusLabel, taskStatusVariant } from '@/features/tasks/taskLabels'

const nextActions: Record<TaskStatus, Array<{ target: TaskStatus; label: string }>> = {
  TODO: [{ target: 'IN_PROGRESS', label: '开始' }, { target: 'ABANDONED', label: '放弃' }],
  IN_PROGRESS: [{ target: 'COMPLETED', label: '完成' }, { target: 'TODO', label: '重置' }, { target: 'ABANDONED', label: '放弃' }],
  COMPLETED: [{ target: 'IN_PROGRESS', label: '重新打开' }],
  ABANDONED: [{ target: 'TODO', label: '恢复' }],
}

function toInputDateTime(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (item: number) => String(item).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function TaskEditor({ task, onSaved }: { task: LearningTask; onSaved: (task: LearningTask) => void }) {
  const updateTask = useUpdateTask()
  const transitionTask = useTransitionTask()
  const [title, setTitle] = useState(task.title)
  const [type, setType] = useState(task.type ?? '')
  const [priority, setPriority] = useState<TaskPriority>(task.priority ?? 'MEDIUM')
  const [estimatedMinutes, setEstimatedMinutes] = useState(task.estimatedMinutes?.toString() ?? '')
  const [dueAt, setDueAt] = useState(toInputDateTime(task.dueAt))
  const [learningGoal, setLearningGoal] = useState(task.learningGoal ?? '')
  const [acceptanceCriteria, setAcceptanceCriteria] = useState(task.acceptanceCriteria ?? '')
  const [verificationMethod, setVerificationMethod] = useState(task.verificationMethod ?? '')
  const [verificationResult, setVerificationResult] = useState(task.verificationResult ?? '')
  const [outputUrl, setOutputUrl] = useState(task.outputUrl ?? '')
  const [error, setError] = useState<string | null>(null)

  const reportError = (caught: Error) => {
    const message = isApiError(caught) || isNetworkError(caught) ? caught.message : '任务操作失败，请稍后重试'
    setError(message)
    pushToast(message, 'error')
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const body: TaskUpdateRequest = {
      title: title.trim(),
      type: type.trim() || undefined,
      priority,
      estimatedMinutes: estimatedMinutes ? Number(estimatedMinutes) : undefined,
      dueAt: dueAt ? new Date(dueAt).toISOString() : undefined,
      learningGoal: learningGoal.trim() || undefined,
      acceptanceCriteria: acceptanceCriteria.trim() || undefined,
      verificationMethod: verificationMethod.trim() || undefined,
      verificationResult: verificationResult.trim() || undefined,
      outputUrl: outputUrl.trim() || undefined,
      knowledgePointIds: task.knowledgePoints?.map((point) => point.id),
      relatedJobIds: task.sourceRefs?.filter((ref) => ref.type === 'JOB').map((ref) => ref.id),
      relatedQuestionIds: task.sourceRefs?.filter((ref) => ref.type === 'QUESTION').map((ref) => ref.id),
    }
    try {
      const saved = await updateTask.mutateAsync({ taskId: task.id, version: task.version, body })
      onSaved(saved)
      pushToast('任务已保存')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const transition = async (targetStatus: TaskStatus) => {
    setError(null)
    try {
      const saved = await transitionTask.mutateAsync({
        taskId: task.id,
        version: task.version,
        body: { targetStatus, verificationResult: targetStatus === 'COMPLETED' ? verificationResult.trim() || undefined : undefined },
      })
      onSaved(saved)
      if (targetStatus === 'COMPLETED') setVerificationResult(saved.verificationResult ?? '')
      pushToast('任务状态已更新')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const pending = updateTask.isPending || transitionTask.isPending
  return (
    <>
      {error ? <div className="conflict-banner"><span>{error}</span></div> : null}
      <section className="card">
        <div className="card-header">
          <h2 className="card-title">任务内容</h2>
          <Badge variant={taskStatusVariant[task.status]}>{taskStatusLabel[task.status]}</Badge>
        </div>
        <div className="card-body">
          <form onSubmit={save} noValidate>
            <div className="form-row">
              <Field label="任务名称" required><Input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} required /></Field>
              <Field label="任务类型"><Input value={type} onChange={(event) => setType(event.target.value)} maxLength={100} /></Field>
            </div>
            <div className="form-row">
              <Field label="优先级"><Select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option><option value="URGENT">紧急</option></Select></Field>
              <Field label="预计耗时（分钟）"><Input type="number" min={1} value={estimatedMinutes} onChange={(event) => setEstimatedMinutes(event.target.value)} /></Field>
              <Field label="截止时间"><Input type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} /></Field>
            </div>
            <Field label="学习目标"><Textarea rows={3} value={learningGoal} onChange={(event) => setLearningGoal(event.target.value)} maxLength={5000} /></Field>
            <div className="form-row">
              <Field label="验收标准"><Textarea rows={3} value={acceptanceCriteria} onChange={(event) => setAcceptanceCriteria(event.target.value)} maxLength={5000} /></Field>
              <Field label="验证方式"><Textarea rows={3} value={verificationMethod} onChange={(event) => setVerificationMethod(event.target.value)} maxLength={1000} /></Field>
            </div>
            <div className="form-row">
              <Field label="验证结果"><Textarea rows={3} value={verificationResult} onChange={(event) => setVerificationResult(event.target.value)} maxLength={5000} /></Field>
              <Field label="产出物链接"><Input value={outputUrl} onChange={(event) => setOutputUrl(event.target.value)} maxLength={2000} /></Field>
            </div>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              <Button variant="primary" type="submit" disabled={pending || !title.trim()}>{updateTask.isPending ? '保存中…' : '保存修改'}</Button>
            </div>
          </form>
        </div>
      </section>
      <section className="card">
        <div className="card-header"><h2 className="card-title">来源与关联</h2></div>
        <div className="card-body">
          {task.knowledgePoints?.length ? <p>知识点：{task.knowledgePoints.map((point) => point.name).join('、')}</p> : <p className="muted">暂无关联知识点</p>}
          {task.sourceRefs?.length ? <div className="stack">{task.sourceRefs.map((ref) => <Link key={`${ref.type}-${ref.id}`} to={ref.type === 'JOB' ? `/jobs/${ref.id}` : ref.type === 'QUESTION' ? '/knowledge-points/weak' : '/tasks'}>{ref.label}</Link>)}</div> : <p className="muted">暂无来源引用</p>}
        </div>
      </section>
      <section className="card">
        <div className="card-header"><h2 className="card-title">状态操作</h2></div>
        <div className="card-body flex-row" style={{ justifyContent: 'flex-start' }}>
          {nextActions[task.status].map((action) => <Button key={action.target} variant={action.target === 'COMPLETED' ? 'primary' : 'default'} type="button" disabled={pending} onClick={() => transition(action.target)}>{action.label}</Button>)}
        </div>
      </section>
    </>
  )
}

export function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>()
  const navigate = useNavigate()
  const query = useTask(taskId)
  if (query.isLoading) return <Spinner label="加载任务详情…" />
  if (query.error || !query.data) return <ErrorState error={query.error ?? new Error('任务不存在')} onRetry={() => query.refetch()} />
  return (
    <div>
      <div className="page-header">
        <div><h1 className="page-title">任务详情</h1><p className="page-subtitle">查看来源、编辑任务内容并更新状态</p></div>
        <Button variant="ghost" size="sm" type="button" onClick={() => navigate('/tasks')}>返回任务列表</Button>
      </div>
      <TaskEditor key={`${query.data.id}:${query.data.version}`} task={query.data} onSaved={() => { void query.refetch() }} />
    </div>
  )
}
