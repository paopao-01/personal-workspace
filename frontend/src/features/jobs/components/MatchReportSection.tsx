import { isApiError, isNetworkError } from '@/api/errors'
import {
  useGenerateMatchReport,
  useLatestMatchReport,
} from '@/api/jobs/useMatchReportQueries'
import type { MatchReport } from '@/api/jobs/matchReportApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Spinner } from '@/components/ui/Spinner'
import { formatDateTime, gapStatusLabel } from '@/features/jobs/statusLabels'
import { useMemo } from 'react'

const suggestionLabels: Record<string, string> = {
  STRONG_MATCH: '匹配度高',
  PARTIAL_MATCH: '部分匹配',
  LOW_MATCH: '匹配度低',
  NEED_MORE_INFO: '资料不足',
}

const suggestionVariants: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  STRONG_MATCH: 'success',
  PARTIAL_MATCH: 'warning',
  LOW_MATCH: 'danger',
  NEED_MORE_INFO: 'info',
}

function scoreLabel(numerator: number, denominator: number): string {
  if (denominator === 0) return '暂无可计分要求'
  return `${numerator} / ${denominator}`
}

export function MatchReportSection({ jobId }: { jobId: string }) {
  const reportQuery = useLatestMatchReport(jobId)
  const generateMutation = useGenerateMatchReport()
  const report: MatchReport | undefined = useMemo(() => reportQuery.data, [reportQuery.data])

  const reportError = (caught: Error) => {
    const message =
      isApiError(caught) || isNetworkError(caught)
        ? caught.message
        : '报告生成失败，请稍后重试'
    pushToast(message, 'error')
  }

  const generate = () => {
    generateMutation.mutate(jobId, {
      onSuccess: () => pushToast('匹配报告已生成'),
      onError: reportError,
    })
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">匹配报告</h2>
        <Button
          variant="primary"
          size="sm"
          type="button"
          disabled={generateMutation.isPending}
          onClick={generate}
        >
          {generateMutation.isPending ? '生成中…' : report ? '重新生成' : '生成匹配报告'}
        </Button>
      </div>
      <div className="card-body">
        {reportQuery.isLoading ? (
          <Spinner label="加载匹配报告…" />
        ) : !report ? (
          <EmptyState
            icon="🎯"
            text="尚未生成匹配报告。确认岗位要求并补充自评与证据后，生成可解释的匹配结论（不输出综合百分比）。"
          />
        ) : (
          <>
            {report.stale ? (
              <div className="conflict-banner" style={{ marginBottom: 12 }}>
                <span>岗位要求或用户资料已变化，本报告可能过期，建议重新生成。</span>
              </div>
            ) : null}
            <div className="plain-block">
              <p style={{ margin: 0 }}>
                <Badge variant={suggestionVariants[report.suggestion.type] ?? 'info'}>
                  {suggestionLabels[report.suggestion.type] ?? report.suggestion.type}
                </Badge>
                <span className="muted" style={{ marginLeft: 8 }}>
                  {formatDateTime(report.generatedAt)} · 规则 {report.ruleVersion}
                </span>
              </p>
              <ul className="muted" style={{ margin: '8px 0 0', paddingLeft: 18 }}>
                {report.suggestion.reasons.map((reason) => (
                  <li key={reason}>{reason}</li>
                ))}
              </ul>
            </div>
            <div className="requirement-meta" style={{ margin: '12px 0' }}>
              <Badge variant="primary">
                必须要求：{scoreLabel(report.mustScore.numerator, report.mustScore.denominator)}（权重 {report.mustScore.weight}）
              </Badge>
              <Badge variant="subtle">
                加分要求：{scoreLabel(report.bonusScore.numerator, report.bonusScore.denominator)}（权重 {report.bonusScore.weight}）
              </Badge>
              <Badge variant="subtle">
                必须汇总：满足 {report.mustSummary.satisfiedWithEvidence} · 自报无证据 {report.mustSummary.selfReportedNoEvidence} · 未满足 {report.mustSummary.notMet} · 资料不足 {report.mustSummary.insufficientInfo}
              </Badge>
            </div>
            <p className="form-hint" style={{ margin: 0 }}>
              资料不足的要求不计入分母，不会按零分处理。
            </p>
            <div className="stack" style={{ marginTop: 12 }}>
              {report.requirements.map((item) => (
                <div key={item.requirementId} className="plain-block">
                  <p style={{ margin: 0 }}>
                    <strong>{item.normalizedName ?? '未命名要求'}</strong>
                    <span className="muted">（{item.type}）</span>
                    <span style={{ marginLeft: 8 }}>
                      <Badge
                        variant={
                          item.status === 'SATISFIED_WITH_EVIDENCE'
                            ? 'success'
                            : item.status === 'NOT_MET'
                              ? 'danger'
                              : 'info'
                        }
                      >
                        {gapStatusLabel[item.status]}
                      </Badge>
                    </span>
                  </p>
                  <p className="muted" style={{ margin: '4px 0 0' }}>{item.rawText}</p>
                  {item.reasons.map((reason) => (
                    <p className="muted" style={{ margin: 0 }} key={reason}>· {reason}</p>
                  ))}
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </section>
  )
}
