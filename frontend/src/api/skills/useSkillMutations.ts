import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createSkill,
  updateSelfLevel,
  type SkillCreateRequest,
  type SelfLevelUpdateRequest,
  type SkillProfile,
} from '@/api/skills/skillApi'

export function useCreateSkill() {
  const queryClient = useQueryClient()
  return useMutation<SkillProfile, Error, SkillCreateRequest>({
    mutationFn: createSkill,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['skills', 'profile'] }),
  })
}

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
