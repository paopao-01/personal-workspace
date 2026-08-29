import { useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { setDisplayTimeZone } from '@/api/settings/displayTimeZone'
import { useSettings } from '@/api/settings/useSettingsQueries'
import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'

export function AppLayout() {
  const { data: settings } = useSettings()

  useEffect(() => {
    setDisplayTimeZone(settings?.timeZone)
  }, [settings?.timeZone])

  return (
    <div className="app-container">
      <Sidebar />
      <div className="app-main">
        <TopBar />
        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
