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
import { InterviewPreparationPage } from '@/features/interviews/InterviewPreparationPage'
import { InterviewReviewPage } from '@/features/reviews/InterviewReviewPage'
import { WeakKnowledgePointsPage } from '@/features/reviews/WeakKnowledgePointsPage'
import { TaskListPage } from '@/features/tasks/TaskListPage'
import { ProjectsPage } from '@/features/projects/ProjectsPage'
import { SettingsPage } from '@/features/settings/SettingsPage'
import { NotificationsPage } from '@/features/notifications/NotificationsPage'

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
        <Route path="/interviews/:interviewId/preparation" element={<InterviewPreparationPage />} />
        <Route path="/interviews/:interviewId/review" element={<InterviewReviewPage />} />
        <Route path="/knowledge-points/weak" element={<WeakKnowledgePointsPage />} />
        <Route path="/tasks" element={<TaskListPage />} />
        <Route path="/projects" element={<ProjectsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  )
}
