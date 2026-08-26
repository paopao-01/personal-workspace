import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { JobListPage } from '@/features/jobs/JobListPage'
import { JobCreatePage } from '@/features/jobs/JobCreatePage'
import { JobDetailPage } from '@/features/jobs/JobDetailPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { ApplicationCreatePage } from '@/features/applications/ApplicationCreatePage'
import { ApplicationDetailPage } from '@/features/applications/ApplicationDetailPage'
import { InterviewDetailPage } from '@/features/interviews/InterviewDetailPage'
import { InterviewListPage } from '@/features/interviews/InterviewListPage'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/jobs" element={<JobListPage />} />
        <Route path="/jobs/new" element={<JobCreatePage />} />
        <Route path="/jobs/:jobId" element={<JobDetailPage />} />
        <Route
          path="/applications/new"
          element={<ApplicationCreatePage />}
        />
        <Route
          path="/applications/:applicationId"
          element={<ApplicationDetailPage />}
        />
        <Route path="/interviews" element={<InterviewListPage />} />
        <Route path="/interviews/:interviewId" element={<InterviewDetailPage />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  )
}
