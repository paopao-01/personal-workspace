import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { answerMockInterview, evaluateMockInterviewAnswer, getMockInterview, getMockInterviewEvaluationSummary, getMockInterviewTurns, transitionMockInterview } from '@/api/mockInterviewApi'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'

export function MockInterviewPage() {
  const { mockInterviewId: id } = useParams()
  const nav = useNavigate()
  const qc = useQueryClient()
  const [answer, setAnswer] = useState('')
  const session = useQuery({ queryKey: ['mock-interview', id], queryFn: () => getMockInterview(id!), enabled: !!id, refetchInterval: query => query.state.data?.status === 'DRAFT' || !!query.state.data?.followUpAiJobId ? 1000 : false })
  const turns = useQuery({ queryKey: ['mock-interview', id, 'turns'], queryFn: () => getMockInterviewTurns(id!), enabled: session.data?.status === 'ACTIVE' || session.data?.status === 'COMPLETED', refetchInterval: query => query.state.data?.some(turn => !!turn.evaluationAiJobId && turn.evaluationScore === null) || !!session.data?.followUpAiJobId ? 1000 : false })
  const summary = useQuery({ queryKey: ['mock-interview', 'evaluation-summary'], queryFn: getMockInterviewEvaluationSummary, refetchInterval: () => turns.data?.some(turn => !!turn.evaluationAiJobId && turn.evaluationScore === null) ? 1000 : false })
  const latestTurnId = turns.data?.at(-1)?.id
  const latestTurnSpeaker = turns.data?.at(-1)?.speaker
  const evaluatedTurnCount = turns.data?.filter(turn => turn.evaluationScore !== null).length ?? 0
  const refresh = () => Promise.all([
    qc.invalidateQueries({ queryKey: ['mock-interview', id] }),
    qc.invalidateQueries({ queryKey: ['mock-interview', id, 'turns'] }),
    qc.invalidateQueries({ queryKey: ['mock-interview', 'evaluation-summary'] }),
  ])
  const act = useMutation({ mutationFn: (targetStatus: 'COMPLETED' | 'CANCELED') => transitionMockInterview(id!, session.data!.version, targetStatus), onSuccess: refresh })
  const reply = useMutation({ mutationFn: () => answerMockInterview(id!, session.data!.version, answer), onSuccess: () => { setAnswer(''); return refresh() } })
  const evaluate = useMutation({ mutationFn: (turnId: string) => evaluateMockInterviewAnswer(id!, turnId, session.data!.version), onSuccess: refresh })

  useEffect(() => {
    if (session.data?.status === 'ACTIVE' && !session.data.followUpAiJobId && latestTurnSpeaker === 'USER') {
      void qc.invalidateQueries({ queryKey: ['mock-interview', id, 'turns'] })
    }
  }, [id, latestTurnId, latestTurnSpeaker, qc, session.data?.followUpAiJobId, session.data?.status])

  useEffect(() => {
    if (evaluatedTurnCount > 0) void qc.invalidateQueries({ queryKey: ['mock-interview', 'evaluation-summary'] })
  }, [evaluatedTurnCount, qc])

  if (session.isLoading) return <p>正在加载模拟面试…</p>
  if (session.error) return <ErrorState error={session.error} onRetry={() => session.refetch()} />

  const s = session.data!
  const latest = turns.data?.at(-1)
  const awaitingFollowUp = !!s.followUpAiJobId
  return <div>
    <div className="page-header"><div><h1 className="page-title">项目模拟面试</h1><p className="page-subtitle">AI 内容仅用于练习；不会修改项目、技能或任何用户事实。</p></div><Button variant="default" onClick={() => nav('/projects')}>返回项目</Button></div>
    {summary.data ? <section className="card"><div className="card-body"><strong>评分练习统计</strong><p className="muted">仅汇总已完成的 AI 练习评分，不代表能力等级或改善趋势。</p>{summary.data.averageScore == null ? <p>信息不足：当前仅有 {summary.data.evaluatedAnswerCount} 条已完成评分；至少需要 2 条才显示平均分。</p> : <p>已完成 {summary.data.evaluatedAnswerCount} 条评分，覆盖 {summary.data.evaluatedSessionCount} 个会话，平均分：{summary.data.averageScore?.toFixed(1)}/5。</p>}<p>分布：{summary.data.scoreDistribution.map(item => `${item.score} 分 ${item.count} 条`).join(' · ')}</p>{summary.data.recentScores.length > 0 ? <p className="muted">最近评分：{summary.data.recentScores.map(item => `${item.score}/5`).join('、')}</p> : null}</div></section> : null}
    {summary.error ? <p className="muted">评分统计暂时无法加载。</p> : null}
    {s.status === 'DRAFT' ? <section className="card"><div className="card-body"><p>正在根据保存的项目快照生成讲解稿和首个高频追问…</p></div></section> : null}
    {turns.data?.map(turn => <section className="card" key={turn.id}><div className="card-body"><strong>{turn.speaker === 'USER' ? '我的作答' : turn.turnNumber === 1 ? '项目讲解稿' : '高频追问'}</strong><p style={{ whiteSpace: 'pre-wrap' }}>{turn.content}</p>{turn.speaker === 'USER' && turn.evaluationScore !== null ? <div><strong>AI 练习评分：{turn.evaluationScore}/5</strong><p>{turn.evaluationFeedback}</p><p className="muted">评分依据：{turn.evaluationRationale}</p></div> : null}{turn.speaker === 'USER' && turn.evaluationAiJobId && turn.evaluationScore === null ? <p className="muted">正在生成 AI 练习评分…</p> : null}{turn.speaker === 'USER' && !turn.evaluationAiJobId && s.status !== 'CANCELED' ? <Button variant="default" disabled={evaluate.isPending} onClick={() => evaluate.mutate(turn.id)}>获取 AI 评分</Button> : null}</div></section>)}
    {evaluate.error ? <p role="alert">评分请求失败，请重试。</p> : null}
    {s.status === 'ACTIVE' && latest?.speaker === 'AI' && !awaitingFollowUp ? <section className="card"><div className="card-body"><label htmlFor="mock-answer">我的作答</label><textarea id="mock-answer" value={answer} maxLength={10000} onChange={event => setAnswer(event.target.value)} rows={6} /><div className="flex-row"><Button variant="primary" disabled={reply.isPending || !answer.trim()} onClick={() => reply.mutate()}>保存作答并生成追问</Button></div>{reply.error ? <p role="alert">保存失败，请重试。</p> : null}</div></section> : null}
    {s.status === 'ACTIVE' && awaitingFollowUp ? <p className="muted">作答已保存，正在生成下一道追问…</p> : null}
    {s.status === 'ACTIVE' ? <div className="flex-row"><Button variant="primary" disabled={act.isPending} onClick={() => act.mutate('COMPLETED')}>结束练习</Button><Button variant="default" disabled={act.isPending} onClick={() => act.mutate('CANCELED')}>取消会话</Button></div> : null}
    {s.status === 'COMPLETED' || s.status === 'CANCELED' ? <p className="muted">会话已{ s.status === 'COMPLETED' ? '结束' : '取消'}。</p> : null}
  </div>
}
