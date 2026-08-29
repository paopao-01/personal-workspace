import { useQuery } from '@tanstack/react-query'
import {
  listSkillProfiles,
  type SkillProfile,
} from '@/api/skills/skillApi'

export function useSkillProfiles() {
  return useQuery<SkillProfile[]>({
    queryKey: ['skills', 'profile'],
    queryFn: listSkillProfiles,
  })
}
