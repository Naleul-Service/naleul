import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/client/api'

/**
 * 백엔드 UserColorResponse.
 * ⚠️ Lombok `boolean isDefault` → Jackson 이 "default" 로 내보내요. 두 이름 모두 대비.
 */
interface UserColorRaw {
  userColorId: number
  colorCode: string
  isDefault?: boolean
  default?: boolean
}

export interface UserColor {
  userColorId: number
  /** 항상 "#RRGGBB" 형태 */
  hex: string
  isDefault: boolean
}

const toHex = (code: string) => (code.startsWith('#') ? code : `#${code}`)

export const colorKeys = { list: ['user-colors'] as const }

export function useUserColors() {
  return useQuery({
    queryKey: colorKeys.list,
    queryFn: async (): Promise<UserColor[]> => {
      const list = (await api.get<UserColorRaw[]>('/v1/user-colors')) ?? []
      return list.map((c) => ({
        userColorId: c.userColorId,
        hex: toHex(c.colorCode),
        isDefault: !!(c.isDefault ?? c.default),
      }))
    },
    staleTime: 5 * 60_000,
  })
}
