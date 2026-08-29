import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  purgeTrashItem,
  restoreTrashItem,
  type TrashItem,
} from '@/api/settings/trashApi'

export function useRestoreTrashItem() {
  const queryClient = useQueryClient()
  return useMutation<TrashItem, Error, string>({
    mutationFn: restoreTrashItem,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['trash'] })
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      queryClient.invalidateQueries({ queryKey: ['evidence'] })
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
      queryClient.invalidateQueries({ queryKey: ['interviews'] })
    },
  })
}

export function usePurgeTrashItem() {
  const queryClient = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: purgeTrashItem,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['trash'] })
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      queryClient.invalidateQueries({ queryKey: ['evidence'] })
    },
  })
}
