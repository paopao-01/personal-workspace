import { Badge } from '@/components/ui/Badge'
import type { ApplicationDetail } from '@/api/applications/applicationApi'
import { applicationStatusLabel, applicationStatusVariant } from '@/features/applications/applicationStatusLabels'

/**
 * 区1：投递摘要。展示岗位信息（来自 ApplicationDetail.job）+ 投递元数据。
 */
export function ApplicationSummarySection({
  detail,
}: {
  detail: ApplicationDetail
}) {
  const job = detail.job
  return (
    <section className="card detail-summary">
      <div className="card-header">
        <h2 className="card-title">投递摘要</h2>
      </div>
      <div className="card-body">
        <dl>
          <dt>公司</dt>
          <dd>{job?.companyName ?? '—'}</dd>
          <dt>岗位</dt>
          <dd>{job?.title ?? '—'}</dd>
          <dt>地点</dt>
          <dd className="muted">{job?.location || '—'}</dd>
          <dt>投递渠道</dt>
          <dd>{detail.channel}</dd>
          <dt>投递日期</dt>
          <dd>{detail.appliedAt}</dd>
          <dt>简历版本</dt>
          <dd className="muted">{detail.resumeVersion || '—'}</dd>
          <dt>期望薪资</dt>
          <dd className="muted">{detail.expectedSalary || '—'}</dd>
          <dt>联系人</dt>
          <dd className="muted">{detail.contact || '—'}</dd>
          <dt>当前状态</dt>
          <dd>
            <Badge variant={applicationStatusVariant[detail.status]}>
              {applicationStatusLabel[detail.status]}
            </Badge>
          </dd>
        </dl>
        {detail.notes ? (
          <div style={{ marginTop: 16 }}>
            <div className="form-label" style={{ marginBottom: 4 }}>
              备注
            </div>
            <p className="muted" style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
              {detail.notes}
            </p>
          </div>
        ) : null}
      </div>
    </section>
  )
}
