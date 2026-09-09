import { useState } from 'react'
import { useFunnel, type FunnelGranularity } from '@/api/analytics/analyticsApi'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'

/** 将 datetime-local 本地时间值转为 ISO-8601 UTC 字符串（后端 from/to 参数用）。空返回空串。 */
function toUtcIso(localValue: string): string {
  if (!localValue) return ''
  const d = new Date(localValue)
  if (Number.isNaN(d.getTime())) return ''
  return d.toISOString()
}

function rateLabel(rate: number | undefined): string {
  if (rate === undefined) return '—'
  return `${(rate * 100).toFixed(0)}%`
}

export function FunnelPage() {
  const [fromLocal, setFromLocal] = useState('')
  const [toLocal, setToLocal] = useState('')
  const [granularity, setGranularity] = useState<FunnelGranularity>('day')
  const [params, setParams] = useState<{
    from?: string
    to?: string
    granularity?: FunnelGranularity
  } | null>({ granularity: 'day' })
  const query = useFunnel(params)

  const apply = () => {
    setParams({
      from: fromLocal ? toUtcIso(fromLocal) : undefined,
      to: toLocal ? toUtcIso(toLocal) : undefined,
      granularity,
    })
  }

  const clear = () => {
    setFromLocal('')
    setToLocal('')
    setGranularity('day')
    setParams({ granularity: 'day' })
  }

  if (query.isLoading) {
    return <Spinner label="加载投递漏斗转化趋势…" />
  }
  if (query.error || !query.data) {
    return (
      <ErrorState
        error={query.error ?? new Error('漏斗转化加载失败')}
        onRetry={() => query.refetch()}
      />
    )
  }

  const buckets = query.data
  const noData = buckets.length === 0

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">投递漏斗转化趋势</h1>
          <p className="page-subtitle">
            按投递日期与时间粒度分组聚合投递、面试与 Offer 原始计数与转化率，只展示有投递记录的桶。
          </p>
        </div>
      </div>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">筛选与粒度</h2>
        </div>
        <div className="card-body">
          <div className="form-row">
            <Field label="起始时间">
              <Input
                type="datetime-local"
                value={fromLocal}
                onChange={(event) => setFromLocal(event.target.value)}
                aria-label="按投递起始时间过滤"
              />
            </Field>
            <Field label="结束时间">
              <Input
                type="datetime-local"
                value={toLocal}
                onChange={(event) => setToLocal(event.target.value)}
                aria-label="按投递结束时间过滤"
              />
            </Field>
            <Field label="分组粒度">
              <Select
                value={granularity}
                onChange={(event) => setGranularity(event.target.value as FunnelGranularity)}
                aria-label="趋势分组粒度"
              >
                <option value="day">按天</option>
                <option value="hour">按小时</option>
              </Select>
            </Field>
          </div>
          <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="ghost" type="button" onClick={clear}>
              清空
            </Button>
            <Button variant="primary" type="button" onClick={apply}>
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
              text="当前范围内暂无投递记录；创建并提交投递后，这里会按时间粒度聚合漏斗转化趋势。"
            />
          </div>
        </section>
      ) : (
        <>
          <section className="card">
            <div className="card-header">
              <h2 className="card-title">漏斗转化（按 date 升序）</h2>
            </div>
            <div className="card-body">
              <table className="table">
                <thead>
                  <tr>
                    <th>日期</th>
                    <th>投递数</th>
                    <th>面试数</th>
                    <th>Offer 数</th>
                    <th>面试转化率</th>
                    <th>Offer 转化率</th>
                  </tr>
                </thead>
                <tbody>
                  {buckets.map((bucket) => (
                    <tr key={bucket.date}>
                      <td>{bucket.date}</td>
                      <td>{bucket.applied}</td>
                      <td>{bucket.interviewed}</td>
                      <td>{bucket.offered}</td>
                      <td>{rateLabel(bucket.interviewRate)}</td>
                      <td>{rateLabel(bucket.offerRate)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="muted">
                计数采用状态近似口径（面试数 = INTERVIEWING/OFFER，Offer 数 = OFFER），不 JOIN 面试记录；
                分母为已投递数（applied），applied 为 0 时转化率兜底 0；只展示有投递记录的桶，缺失日期不补 0。
                仅展示原始计数与转化率，不推断趋势结论、能力等级或行动建议。
              </p>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
