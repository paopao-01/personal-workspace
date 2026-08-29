import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  updateSelfLevel,
  type SelfLevelUpdateRequest,
  type SkillProfile,
} from '@/api/skills/skillApi'

export function useUpdateSelfLevel() {
  const queryClient = useQueryClient()
  return useMutation<
    SkillProfile,
    Error,
    { skillId: string; version: number; body: SelfLevelUpdateRequest }
  >({
    mutationFn: ({ skillId, version, body }) => updateSelfLevel(skillId, version, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'profile'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}
