import { useState } from 'react'
import { useReviewAnalysis } from '@/api/reviews/useReviewQueries'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { answerStatusLabel } from '@/features/reviews/reviewLabels'
import type { ReviewAnalysis } from '@/api/reviews/reviewApi'

function QuestionStatsSection({ analysis }: { analysis: ReviewAnalysis }) {
  const stats = analysis.questionStats
  const rate =
    stats.fullyAnswered.denominator > 0
      ? `${stats.fullyAnswered.numerator}/${stats.fullyAnswered.denominator}`
      : '—'
  const summary = analysis.interviewResultSummary
  return (
    <section className="card detail-summary">
      <div className="card-header">
        <h2 className="card-title">问题回答情况</h2>
      </div>
      <div className="card-body">
        <dl>
          <dt>参与复盘</dt>
          <dd>{analysis.reviewCount}（含草稿与已完成）</dd>
          <dt>问题总数</dt>
          <dd>{stats.totalCount}</dd>
          <dt>{answerStatusLabel.FULLY_ANSWERED}</dt>
          <dd>
            {stats.fullyAnsweredCount}
            <span className="muted"> · 完全答出率 {rate}</span>
          </dd>
          <dt>{answerStatusLabel.PARTIALLY_ANSWERED}</dt>
          <dd>{stats.partiallyAnsweredCount}</dd>
          <dt>{answerStatusLabel.UNANSWERED}</dt>
          <dd>{stats.unansweredCount}</dd>
          <dt>已填写面试结果</dt>
          <dd>
            {summary.withResultCount}
            <span className="muted">
              {' '}
              · 通过 {summary.passedCount} / 未通过 {summary.failedCount} / 暂不确认{' '}
              {summary.pendingCount}
            </span>
          </dd>
        </dl>
        <p className="muted">
          完全答出率以分子/分母展示（分母为已标记回答状态的问题数），不输出综合能力分数；样本量不足时仅参考原始数量。
        </p>
      </div>
    </section>
  )
}

function KnowledgePointStatsSection({ analysis }: { analysis: ReviewAnalysis }) {
  const items = analysis.knowledgePointStats
  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">知识点表现</h2>
      </div>
      <div className="card-body">
        {items.length === 0 ? (
          <EmptyState icon="□" text="暂无关联知识点的复盘问题" />
        ) : (
          <div>
            {items.map((item) => (
              <div className="requirement-row" key={item.knowledgePoint.id}>
                <div className="requirement-main">
                  <span className="requirement-raw">{item.knowledgePoint.name}</span>
                  <span className="muted">
                    {item.knowledgePoint.category ?? '未分类'} · 共 {item.questionCount} 道题 · 完全答出{' '}
                    {item.fullyAnsweredCount} 道
                  </span>
                </div>
                <div className="requirement-actions">
                  {item.notFullyAnsweredCount > 0 ? (
                    <Badge variant="warning">待巩固 {item.notFullyAnsweredCount} 道</Badge>
                  ) : (
                    <Badge variant="success">全部答出</Badge>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

function QuestionTypeStatsSection({ analysis }: { analysis: ReviewAnalysis }) {
  const items = analysis.questionTypeStats
  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">问题类型分布</h2>
      </div>
      <div className="card-body">
        {items.length === 0 ? (
          <EmptyState icon="□" text="暂无复盘问题" />
        ) : (
          <div>
            {items.map((item, index) => (
              <div className="requirement-row" key={`${item.type ?? '未填写'}-${index}`}>
                <div className="requirement-main">
                  <span className="requirement-raw">{item.type ?? '未填写类型'}</span>
                  <span className="muted">
                    共 {item.questionCount} 道题 · 完全答出 {item.fullyAnsweredCount} 道
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

export function ReviewAnalysisPage() {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [jobId, setJobId] = useState('')
  const [compareFrom, setCompareFrom] = useState('')
  const [compareTo, setCompareTo] = useState('')
  const [params, setParams] = useState<{ from?: string; to?: string; jobId?: string; compareFrom?: string; compareTo?: string }>({})
  const analysisQuery = useReviewAnalysis(params)

  if (analysisQuery.isLoading) {
    return <Spinner label="加载复盘分析…" />
  }
  if (analysisQuery.error || !analysisQuery.data) {
    return (
      <ErrorState
        error={analysisQuery.error ?? new Error('复盘分析加载失败')}
        onRetry={() => analysisQuery.refetch()}
      />
    )
  }

  const analysis = analysisQuery.data
  const rangeLabel = [analysis.timeRange.from ?? '最早', analysis.timeRange.to ?? '今天'].join(' ~ ')
  const noData = analysis.reviewCount === 0 && analysis.questionStats.totalCount === 0

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">复盘分析</h1>
          <p className="page-subtitle">跨面试聚合复盘表现，按面试开始日期统计。当前范围：{rangeLabel}</p>
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
            <Field label="岗位 ID" hint="可选；只统计该岗位的复盘">
              <Input value={jobId} onChange={(event) => setJobId(event.target.value)} placeholder="岗位 UUID" />
            </Field>
          </div>
          <div className="form-row">
            <Field label="对比开始日期" hint="同时填写两个日期后显示薄弱点变化">
              <Input type="date" value={compareFrom} onChange={(event) => setCompareFrom(event.target.value)} />
            </Field>
            <Field label="对比结束日期">
              <Input type="date" value={compareTo} onChange={(event) => setCompareTo(event.target.value)} />
            </Field>
          </div>
          <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
            <Button
              variant="ghost"
              type="button"
              onClick={() => {
                setFrom('')
                setTo('')
                setJobId('')
                setCompareFrom('')
                setCompareTo('')
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
                  jobId: jobId.trim() || undefined,
                  compareFrom: compareFrom || undefined,
                  compareTo: compareTo || undefined,
                })
              }
            >
              查询
            </Button>
          </div>
        </div>
      </section>

      {noData ? (
        <section className="card">
          <div className="card-body">
            <EmptyState
              icon="□"
              text="当前范围内暂无复盘记录；完成面试并保存复盘后，这里会聚合问题回答与知识点表现。"
            />
          </div>
        </section>
      ) : (
        <>
          <QuestionStatsSection analysis={analysis} />
          <KnowledgePointStatsSection analysis={analysis} />
          <QuestionTypeStatsSection analysis={analysis} />
          {analysis.weakPointComparison ? (
            <section className="card">
              <div className="card-header"><h2 className="card-title">薄弱点改善对比</h2></div>
              <div className="card-body">
                <p className="muted">对比窗口：{analysis.weakPointComparison.compareTimeRange.from ?? '最早'} ~ {analysis.weakPointComparison.compareTimeRange.to ?? '今天'}；变化值 = 当前窗口 - 对比窗口，负数表示薄弱次数下降。</p>
                {analysis.weakPointComparison.items.length === 0 ? <EmptyState icon="□" text="两个窗口暂无可比较的薄弱知识点" /> : (
                  <div>
                    {analysis.weakPointComparison.items.map((item) => (
                      <div className="requirement-row" key={item.knowledgePoint.id}>
                        <div className="requirement-main">
                          <span className="requirement-raw">{item.knowledgePoint.name}</span>
                          <span className="muted">当前 {item.currentWeightedWeaknessCount} · 对比 {item.compareWeightedWeaknessCount} · 题数 {item.currentQuestionCount}/{item.compareQuestionCount}</span>
                        </div>
                        <Badge variant={item.delta < 0 ? 'success' : item.delta > 0 ? 'warning' : 'subtle'}>
                          {item.delta > 0 ? '+' : ''}{item.delta}
                        </Badge>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </section>
          ) : null}
        </>
      )}
    </div>
  )
}
