import { useQuery } from '@tanstack/react-query'
import {
  listSkillProfiles,
  listSelfLevelHistory,
  type SkillProfile,
} from '@/api/skills/skillApi'

export function useSkillProfiles() {
  return useQuery<SkillProfile[]>({
    queryKey: ['skills', 'profile'],
    queryFn: listSkillProfiles,
  })
}

export function useSelfLevelHistory(skillId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ['skills', 'self-level-history', skillId],
    queryFn: () => listSelfLevelHistory(skillId as string),
    enabled: enabled && !!skillId,
  })
}
