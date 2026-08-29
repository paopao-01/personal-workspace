import { NavLink } from 'react-router-dom'

interface NavItem {
  to: string
  label: string
  enabled: boolean
}

const items: NavItem[] = [
  { to: '/dashboard', label: '首页工作台', enabled: true },
  { to: '/jobs', label: '岗位与投递', enabled: true },
  { to: '/interviews', label: '面试中心', enabled: true },
  { to: '/knowledge-points/weak', label: '薄弱知识点', enabled: true },
  { to: '/reviews/analysis', label: '复盘分析', enabled: true },
  { to: '/tasks', label: '学习任务', enabled: true },
  { to: '/skills', label: '技能画像', enabled: true },
  { to: '/projects', label: '项目与证据', enabled: true },
  { to: '/settings', label: '设置', enabled: true },
]

export function Sidebar() {
  return (
    <aside className="app-sidebar">
      <div className="nav-brand">JobHub</div>
      <nav aria-label="主导航">
        {items.map((item) =>
          item.enabled ? (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `nav-item${isActive ? ' active' : ''}`
              }
            >
              {item.label}
            </NavLink>
          ) : (
            <button
              key={item.to}
              className="nav-item"
              disabled
              title="即将推出（后续里程碑）"
            >
              {item.label}
            </button>
          ),
        )}
      </nav>
    </aside>
  )
}
