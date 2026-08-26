import { NavLink } from 'react-router-dom'

interface NavItem {
  to: string
  label: string
  enabled: boolean
}

const items: NavItem[] = [
  { to: '/dashboard', label: '首页工作台', enabled: true },
  { to: '/jobs', label: '岗位与投递', enabled: true },
  { to: '/interviews', label: '面试中心', enabled: false },
  { to: '/tasks', label: '学习任务', enabled: false },
  { to: '/skills', label: '能力与证据', enabled: false },
  { to: '/settings', label: '设置', enabled: false },
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
