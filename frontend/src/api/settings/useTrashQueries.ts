import { useQuery } from '@tanstack/react-query'
import { listTrash, type TrashItem } from '@/api/settings/trashApi'

export function useTrash() {
  return useQuery<TrashItem[]>({
    queryKey: ['trash'],
    queryFn: listTrash,
  })
}
