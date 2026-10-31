import { useMutation } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { api, logout } from '@/lib/client/api'
import { trackBeforeLeave } from '@/lib/client/analytics'

/** 백엔드 UserNicknameUpdateRequest 의 @Pattern 과 같아요 */
export const NICKNAME_PATTERN = /^[가-힣a-zA-Z0-9]{2,10}$/
export const NICKNAME_HELP = '특수문자 제외 2~10자 이내로 입력해주세요.'

/** 백엔드 UserWithdrawalReason enum 과 값이 정확히 같아야 해요 */
export const WITHDRAWAL_REASONS = [
  { value: 'LACK_OF_CONTENT', label: '앱 콘텐츠가 부족하거나 만족스럽지 않아서' },
  { value: 'INCONVENIENT_OR_BUGGY', label: '이용이 불편하고 장애가 많아서' },
  { value: 'PREFER_OTHER_SERVICE', label: '다른 서비스가 더 좋아서' },
  { value: 'OTHER', label: '기타' },
] as const

export type WithdrawalReason = (typeof WITHDRAWAL_REASONS)[number]['value']

/**
 * 닉네임 수정. 성공하면 router.refresh() 로 서버 컴포넌트(사이드바·설정 화면)를 다시 그려
 * 바뀐 쿠키의 이름이 바로 보이게 해요.
 */
export function useUpdateNickname() {
  const router = useRouter()
  return useMutation({
    mutationFn: (nickname: string) => api.patch<{ userId: number; nickname: string }>('/me/nickname', { nickname }),
    onSuccess: () => router.refresh(),
  })
}

/** 회원 탈퇴 (DELETE /api/v1/users, body 에 사유). 성공하면 쿠키를 지우고 로그인 화면으로 */
export function useWithdraw() {
  return useMutation({
    mutationFn: (body: { reason: WithdrawalReason; detail: string }) => api.delete<null>('/v1/users', body),
    // 결과 이벤트를 beacon 으로 먼저 보낸 뒤 로그아웃 (곧바로 페이지를 떠나요)
    onSuccess: async (_d, body) => {
      await trackBeforeLeave('withdraw.result', { result: 'success', withdraw_reason: body.reason })
      await logout()
    },
  })
}
