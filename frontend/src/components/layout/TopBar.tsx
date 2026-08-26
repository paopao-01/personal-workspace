import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/Button'

export function TopBar() {
  const navigate = useNavigate()
  return (
    <header className="app-topbar">
      <div className="page-subtitle">Java 后端求职个人工作台</div>
      <Button variant="primary" size="sm" onClick={() => navigate('/jobs/new')}>
        + 新增岗位
      </Button>
    </header>
  )
}
