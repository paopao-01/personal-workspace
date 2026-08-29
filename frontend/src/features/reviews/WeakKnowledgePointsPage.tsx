import { useState } from 'react'
import { useWeakKnowledgePoints } from '@/api/reviews/useReviewQueries'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { answerStatusLabel } from '@/features/reviews/reviewLabels'

export function WeakKnowledgePointsPage() {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [params, setParams] = useState<{ from?: string; to?: string }>({})
  const weakQuery = useWeakKnowledgePoints(params)

  if (weakQuery.isLoading) {
    return <Spinner label="加载薄弱知识点…" />
  }
  if (weakQuery.error) {
    return <ErrorState error={weakQuery.error} onRetry={() => weakQuery.refetch()} />
  }

  const items = weakQuery.data ?? []

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">薄弱知识点</h1>
          <p className="page-subtitle">按面试时间统计，未答出计 1 次，部分答出计 0.5 次。</p>
        </div>
      </div>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">时间范围</h2>
        </div>
        <div className="card-body">
          <div className="form-row">
            <Field label="开始日期">
              <Input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
            </Field>
            <Field label="结束日期">
              <Input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
            </Field>
          </div>
          <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
            <Button
              variant="ghost"
              type="button"
              onClick={() => {
                setFrom('')
                setTo('')
                setParams({})
              }}
            >
              清空
            </Button>
            <Button
              variant="primary"
              type="button"
              onClick={() =>
                setParams({
                  from: from || undefined,
                  to: to || undefined,
                })
              }
            >
              查询
            </Button>
          </div>
        </div>
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">统计结果</h2>
        </div>
        <div className="card-body">
          {items.length === 0 ? (
            <EmptyState icon="□" text="暂无薄弱问题记录" />
          ) : (
            <div>
              {items.map((item) => (
                <div className="requirement-row" key={item.knowledgePoint.id}>
                  <div className="requirement-main">
                    <span className="requirement-raw">{item.knowledgePoint.name}</span>
                    <span className="muted">
                      {item.knowledgePoint.category ?? '未分类'} · {item.questionCount} 道题
                    </span>
                    <div className="flex-row">
                      <Badge variant="warning">
                        加权薄弱次数 {item.weightedWeaknessCount}
                      </Badge>
                    </div>
                    <div>
                      {item.questions.map((question) => (
                        <div className="timeline-item" key={question.id}>
                          <strong>{question.content}</strong>
                          <span className="muted">
                            {answerStatusLabel[question.answerStatus]}
                          </span>
                        </div>
                      ))}
                    </div>
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
