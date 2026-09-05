import { useState } from 'react'
import { useChannelEffectiveness, type ChannelEffectivenessGroup, type ResumeVersionEffectivenessGroup } from '@/api/analytics/analyticsApi'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'

function offerRateLabel(rate: number | null | undefined): string {
  if (rate === null || rate === undefined) return '信息不足'
  return `${(rate * 100).toFixed(0)}%`
}

function ChannelGroupsSection({ groups }: { groups: ChannelEffectivenessGroup[] }) {
  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">按投递渠道</h2>
      </div>
      <div className="card-body">
        {groups.length === 0 ? (
          <EmptyState icon="□" text="暂无已投递记录" />
        ) : (
          <div>
            {groups.map((item, index) => (
              <div className="requirement-row" key={`${item.channel}-${index}`}>
                <div className="requirement-main">
                  <span className="requirement-raw">{item.channel}</span>
                  <span className="muted">
                    投递 {item.applicationCount} · 面试 {item.interviewCount} · Offer {item.offerCount}
                  </span>
                </div>
                <div className="requirement-actions">
                  <span className="muted">Offer 率 {offerRateLabel(item.offerRate)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

function ResumeVersionGroupsSection({ groups }: { groups: ResumeVersionEffectivenessGroup[] }) {
  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">按简历版本</h2>
      </div>
      <div className="card-body">
        {groups.length === 0 ? (
          <EmptyState icon="□" text="暂无已投递记录" />
        ) : (
          <div>
            {groups.map((item, index) => (
              <div className="requirement-row" key={`${item.resumeVersion ?? '未指定'}-${index}`}>
                <div className="requirement-main">
                  <span className="requirement-raw">{item.resumeVersion ?? '未指定版本'}</span>
                  <span className="muted">
                    投递 {item.applicationCount} · 面试 {item.interviewCount} · Offer {item.offerCount}
                  </span>
                </div>
                <div className="requirement-actions">
                  <span className="muted">Offer 率 {offerRateLabel(item.offerRate)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

export function ChannelEffectivenessPage() {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [params, setParams] = useState<{ from?: string; to?: string } | null>({})
  const query = useChannelEffectiveness(params)

  if (query.isLoading) {
    return <Spinner label="加载效果对比…" />
  }
  if (query.error || !query.data) {
    return (
      <ErrorState
        error={query.error ?? new Error('效果对比加载失败')}
        onRetry={() => query.refetch()}
      />
    )
  }

  const report = query.data
  const rangeLabel = [report.from ?? '最早', report.to ?? '今天'].join(' ~ ')
  const noData = report.channelGroups.length === 0 && report.resumeVersionGroups.length === 0

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">渠道与简历版本效果对比</h1>
          <p className="page-subtitle">按投递渠道与简历版本聚合投递、面试与 Offer 原始计数。当前范围：{rangeLabel}</p>
        </div>
      </div>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">投递日期范围</h2>
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

      {noData ? (
        <section className="card">
          <div className="card-body">
            <EmptyState
              icon="□"
              text="当前范围内暂无已投递记录；创建并提交投递后，这里会按渠道与简历版本聚合原始计数。"
            />
          </div>
        </section>
      ) : (
        <>
          <ChannelGroupsSection groups={report.channelGroups} />
          <ResumeVersionGroupsSection groups={report.resumeVersionGroups} />
          <p className="muted">
            计数采用状态近似口径（面试数 = INTERVIEWING/OFFER，Offer 数 = OFFER），不 JOIN 面试记录；渠道与版本按原始填写文本分组，不做归一化或去重。仅展示原始数量，不推断趋势或行动。
          </p>
        </>
      )}
    </div>
  )
}
