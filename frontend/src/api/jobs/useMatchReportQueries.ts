import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  generateMatchReport,
  getLatestMatchReport,
  type MatchReport,
} from '@/api/jobs/matchReportApi'

export function useLatestMatchReport(jobId: string) {
  return useQuery<MatchReport>({
    queryKey: ['jobs', jobId, 'match-report'],
    queryFn: () => getLatestMatchReport(jobId),
    retry: false,
  })
}

export function useGenerateMatchReport() {
  const queryClient = useQueryClient()
  return useMutation<MatchReport, Error, string>({
    mutationFn: generateMatchReport,
    onSuccess: (report) => {
      queryClient.setQueryData(['jobs', report.jobId, 'match-report'], report)
    },
  })
}
