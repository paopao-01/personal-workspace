import { EmptyState } from '@/components/ui/EmptyState'
import type { components } from '@/api/generated/types'

type Interview = components['schemas']['Interview']

/**
 * 区5：面试列表。当前 interviews 恒为空数组（面试模块未实现），
 * 展示空状态占位。预留组件结构，后续填充时改数据源即可。
 */
export function InterviewListSection({
  interviews,
}: {
  interviews: Interview[]
}) {
  if (interviews.length === 0) {
    return (
      <section className="card">
        <div className="card-header">
          <h2 className="card-title">面试列表</h2>
        </div>
        <div className="card-body">
          <EmptyState icon="📅" text="面试管理功能将在后续里程碑上线" />
        </div>
      </section>
    )
  }

  // 面试模块实现后在此渲染表格；当前占位
  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">面试列表</h2>
      </div>
      <div className="card-body" />
    </section>
  )
}
