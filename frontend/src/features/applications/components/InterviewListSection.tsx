import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Table } from '@/components/ui/Table'
import type { Interview } from '@/api/interviews/interviewApi'
import {
  formatInterviewTime,
  interviewResultLabel,
  interviewScheduleLabel,
  interviewScheduleVariant,
} from '@/features/interviews/interviewLabels'

/**
 * 区5：面试列表。当前 interviews 恒为空数组（面试模块未实现），
 * 展示空状态占位。预留组件结构，后续填充时改数据源即可。
 */
export function InterviewListSection({
  interviews,
  canCreate,
  onCreate,
}: {
  interviews: Interview[]
  canCreate: boolean
  onCreate: () => void
}) {
  const navigate = useNavigate()
  if (interviews.length === 0) {
    return (
      <section className="card">
        <div className="card-header">
          <h2 className="card-title">面试列表</h2>
          {canCreate ? <Button variant="primary" size="sm" onClick={onCreate}>安排面试</Button> : null}
        </div>
        <div className="card-body">
          <EmptyState
            icon="📅"
            text={canCreate ? '尚未安排面试' : '当前投递状态暂不能创建面试'}
            action={canCreate ? <Button variant="primary" size="sm" onClick={onCreate}>安排第一场面试</Button> : undefined}
          />
        </div>
      </section>
    )
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">面试列表</h2>
        {canCreate ? <Button variant="primary" size="sm" onClick={onCreate}>安排面试</Button> : null}
      </div>
      <div className="card-body">
        <Table headers={['轮次', '开始时间', '日程状态', '结果', '']}>
          {interviews.map((interview) => (
            <tr key={interview.id}>
              <td>{interview.roundName}</td>
              <td>{formatInterviewTime(interview.startsAt)}</td>
              <td><Badge variant={interviewScheduleVariant[interview.scheduleStatus]}>{interviewScheduleLabel[interview.scheduleStatus]}</Badge></td>
              <td>{interviewResultLabel[interview.result]}</td>
              <td className="text-right"><Button variant="ghost" size="sm" onClick={() => navigate(`/interviews/${interview.id}`)}>查看</Button></td>
            </tr>
          ))}
        </Table>
      </div>
    </section>
  )
}
