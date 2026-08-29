import { useQuery } from '@tanstack/react-query'
import { getSettings, type UserSettings } from '@/api/settings/settingsApi'

export function useSettings() {
  return useQuery<UserSettings>({
    queryKey: ['settings'],
    queryFn: getSettings,
    staleTime: 60_000,
  })
}
