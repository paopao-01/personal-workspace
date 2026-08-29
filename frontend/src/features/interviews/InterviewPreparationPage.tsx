import { Link, useNavigate, useParams } from 'react-router-dom'
import { usePreparationPack } from '@/api/interviews/useInterviewQueries'
import type { components } from '@/api/generated/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Spinner } from '@/components/ui/Spinner'
import { gapStatusLabel, requirementTypeLabel } from '@/features/jobs/statusLabels'
import {
  answerStatusLabel,
} from '@/features/reviews/reviewLabels'
import {
  formatInterviewTime,
  interviewResultLabel,
  interviewScheduleLabel,
  interviewScheduleVariant,
} from '@/features/interviews/interviewLabels'
import { taskPriorityLabel, taskStatusLabel } from '@/features/tasks/taskLabels'

type Schemas = components['schemas']
type PreparationItem = Schemas['PreparationItem']

const itemTypeLabel: Record<PreparationItem['type'], string> = {
  REQUIREMENT: '岗位要求',
  PROJECT_CASE: '项目案例',
  QUESTION: '历史问题',
  TASK: '学习任务',
  CHECKLIST: '准备事项',
}

function sourcePath(type: string): string | undefined {
  if (type === 'TASK') return `/tasks`
  if (type === 'QUESTION') return `/knowledge-points/weak`
  return undefined
}

export function InterviewPreparationPage() {
  const { interviewId } = useParams<{ interviewId: string }>()
  const navigate = useNavigate()
  const query = usePreparationPack(interviewId)

  if (query.isLoading) return <Spinner label="加载面试准备包…" />
  if (query.error || !query.data) {
    return (
      <ErrorState
        error={query.error ?? new Error('准备包不存在')}
        onRetry={() => query.refetch()}
        extraAction={<Button variant="ghost" size="sm" onClick={() => navigate('/interviews')}>返回面试中心</Button>}
      />
    )
  }

  const pack = query.data
  const interview = pack.interview
  const checklist = pack.checklist ?? []

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">面试准备包</h1>
          <p className="page-subtitle">
            {interview.roundName} · {formatInterviewTime(interview.startsAt)}
          </p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => navigate(`/interviews/${interview.id}`)}>
          返回面试详情
        </Button>
      </div>

      <section className="card detail-summary">
        <div className="card-header">
          <h2 className="card-title">本场面试</h2>
          <Badge variant={interviewScheduleVariant[interview.scheduleStatus]}>
            {interviewScheduleLabel[interview.scheduleStatus]}
          </Badge>
        </div>
        <div className="card-body">
          <dl>
            <dt>事件时区</dt>
            <dd>{interview.eventTimeZone}</dd>
            <dt>结果</dt>
            <dd>{interviewResultLabel[interview.result]}</dd>
            <dt>会议链接或地址</dt>
            <dd>{interview.meetingUrlOrAddress ?? '—'}</dd>
          </dl>
        </div>
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">本场优先准备项</h2>
        </div>
        <div className="card-body preparation-list">
          {pack.prioritizedItems.length === 0 ? (
            <EmptyState text="暂无优先准备项" />
          ) : (
            pack.prioritizedItems.map((item) => (
              <article key={`${item.type}-${item.title}-${item.priority}`} className="preparation-item">
                <div>
                  <Badge>{itemTypeLabel[item.type]}</Badge>
                  <h3>{item.title}</h3>
                </div>
                <ol className="preparation-reasons">
                  {item.reasons.map((reason) => <li key={reason}>{reason}</li>)}
                </ol>
                <div className="source-ref-list">
                  {item.sourceRefs.map((ref) => {
                    const path = sourcePath(ref.type)
                    return path ? (
                      <Link key={`${ref.type}-${ref.id}`} to={path}>{ref.label}</Link>
                    ) : (
                      <span key={`${ref.type}-${ref.id}`}>{ref.label}</span>
                    )
                  })}
                </div>
              </article>
            ))
          )}
        </div>
      </section>

      <div className="section-grid">
        <section className="card">
          <div className="card-header"><h2 className="card-title">岗位要求与差距</h2></div>
          <div className="card-body">
            {pack.requirements.length === 0 ? (
              <EmptyState text="暂无已确认岗位要求" />
            ) : (
              <table className="table">
                <thead><tr><th>要求</th><th>类型</th><th>差距</th><th>依据</th></tr></thead>
                <tbody>
                  {pack.requirements.map((item) => (
                    <tr key={item.requirement.id}>
                      <td>{item.requirement.normalizedName ?? item.requirement.rawText}</td>
                      <td>{requirementTypeLabel[item.requirement.type]}</td>
                      <td>{gapStatusLabel[item.status]}</td>
                      <td>{item.manualOverrideReason ?? item.requirement.rawText}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <p className="form-hint" style={{ margin: '12px 0 0' }}>
              待确认要求不会作为确定性准备结论展示。
            </p>
          </div>
        </section>

        <section className="card">
          <div className="card-header"><h2 className="card-title">可讲项目案例</h2></div>
          <div className="card-body stack">
            {pack.projectCases.length === 0 ? (
              <EmptyState
                text="待补充项目案例。可在“项目与证据”页面维护真实项目，准备包会自动引用。"
                action={
                  <Link className="btn btn-link" to="/projects">
                    打开项目与证据
                  </Link>
                }
              />
            ) : (
              pack.projectCases.map((project) => (
                <article key={project.id} className="plain-block">
                  <h3>
                    <Link className="btn btn-link" style={{ padding: 0 }} to="/projects">
                      {project.title}
                    </Link>
                  </h3>
                  <p><strong>场景：</strong>{project.scenario}</p>
                  <p><strong>方案：</strong>{project.approach}</p>
                  <p><strong>解决问题：</strong>{project.problemSolved}</p>
                  {(project.evidenceRefs ?? []).length > 0 ? (
                    <p className="muted">证据引用：{(project.evidenceRefs ?? []).map((e) => e.title).join('、')}</p>
                  ) : null}
                </article>
              ))
            )}
          </div>
        </section>

        <section className="card">
          <div className="card-header"><h2 className="card-title">历史问题</h2></div>
          <div className="card-body stack">
            {pack.historicalQuestions.length === 0 ? (
              <EmptyState text="暂无历史问题" />
            ) : (
              pack.historicalQuestions.map((question) => (
                <article key={question.id} className="plain-block">
                  <h3>{question.content}</h3>
                  <p>回答状态：{answerStatusLabel[question.answerStatus]}</p>
                  {question.errorReason ? <p className="muted">错误原因：{question.errorReason}</p> : null}
                  {(question.knowledgePoints ?? []).length > 0 ? (
                    <p className="muted">知识点：{(question.knowledgePoints ?? []).map((kp) => kp.name).join('、')}</p>
                  ) : null}
                </article>
              ))
            )}
          </div>
        </section>

        <section className="card">
          <div className="card-header"><h2 className="card-title">未完成任务</h2></div>
          <div className="card-body stack">
            {pack.openTasks.length === 0 ? (
              <EmptyState text="暂无未完成学习任务" />
            ) : (
              pack.openTasks.map((task) => (
                <article key={task.id} className="plain-block">
                  <h3>{task.title}</h3>
                  <p>{taskStatusLabel[task.status]} · {taskPriorityLabel[task.priority ?? 'MEDIUM']}</p>
                  <p className="muted">验证方式：{task.verificationMethod ?? '信息不足'}</p>
                  {task.dueAt ? <p className="muted">截止：{formatInterviewTime(task.dueAt)}</p> : null}
                </article>
              ))
            )}
          </div>
        </section>

        <section className="card">
          <div className="card-header"><h2 className="card-title">准备事项</h2></div>
          <div className="card-body">
            {checklist.length === 0 ? (
              <EmptyState text="暂无准备事项" />
            ) : (
              <ul className="checklist-readonly">
                {checklist.map((item) => (
                  <li key={item.id}>
                    <span aria-hidden="true">{item.completed ? '✓' : '□'}</span>
                    <span>{item.text}</span>
                  </li>
                ))}
              </ul>
            )}
            <p className="form-hint" style={{ margin: '12px 0 0' }}>
              勾选准备事项不会改变学习任务状态。
            </p>
          </div>
        </section>
      </div>
    </div>
  )
}
