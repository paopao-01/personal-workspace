import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  updateSettings,
  type UserSettings,
  type UserSettingsUpdateRequest,
} from '@/api/settings/settingsApi'

export function useUpdateSettings() {
  const queryClient = useQueryClient()
  return useMutation<
    UserSettings,
    Error,
    { version: number; body: UserSettingsUpdateRequest }
  >({
    mutationFn: ({ version, body }) => updateSettings(version, body),
    onSuccess: (settings) => {
      queryClient.setQueryData(['settings'], settings)
    },
  })
}
