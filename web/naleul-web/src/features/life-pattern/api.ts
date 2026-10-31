import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, isApiError } from '@/lib/client/api'
import { toast } from '@/stores/toastStore'
import type { JavaDayOfWeek } from '@/features/goal/api'
import { lifePatternKeys, timetableKeys } from '@/features/timetable/api'
import type { LifePattern, LifePatternDayTime, LifePatternType } from '@/features/timetable/types'

/**
 * 기본 생활 패턴(고정 시간) 추가·수정·삭제 — 설정 화면용.
 * 목록 조회는 캘린더와 같이 쓰는 useLifePatterns (features/timetable/api) 를 그대로 써요.
 *
 * 백엔드: /api/v1/life-patterns (LifePatternController)
 *  - days 를 빈 배열로 보내면 "매일"
 *  - endTime 이 startTime 보다 이르면 자정을 넘기는 블록 (수면 23:00~07:00)
 *  - 같은 요일끼리 겹치면 409, 10개 넘으면 400 → 백엔드 메시지를 그대로 토스트로 보여줘요
 */

export interface LifePatternInput {
  title: string
  emoji?: string | null
  /** "HH:mm" */
  startTime: string
  endTime: string
  days: JavaDayOfWeek[]
  /** 요일별로 다른 시간. 빈 배열이면 모든 요일 기본 시간 */
  dayTimes?: LifePatternDayTime[]
}

export interface LifePatternCreateInput extends LifePatternInput {
  patternType: LifePatternType
  /** 이날만 (2026-10, 캘린더 "기본 생활 패턴 추가 → 이날만"). "YYYY-MM-DD" — 있으면 그 날짜에만 블록이 생겨요 */
  onlyDate?: string | null
}

const errorMessage = (e: unknown) => (isApiError(e) ? e.message : '문제가 생겼어요. 다시 시도해 주세요.')

/**
 * 고정 시간이 바뀌면 백엔드가 겹치는 자동 배치 블록을 다시 배치해요.
 * 그래서 패턴 목록뿐 아니라 캘린더(TimeTable)도 다시 불러와요.
 */
function useLifePatternMutation<V, R>(fn: (v: V) => Promise<R>, successMessage: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => toast.success(successMessage),
    onError: (e) => toast.error(errorMessage(e)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: lifePatternKeys.list })
      qc.invalidateQueries({ queryKey: timetableKeys.all })
    },
  })
}

export function useCreateLifePattern() {
  return useLifePatternMutation(
    (v: LifePatternCreateInput) => api.post<LifePattern>('/v1/life-patterns', v),
    '고정 시간을 추가했어요.'
  )
}

export function useUpdateLifePattern() {
  return useLifePatternMutation(
    // 종류(patternType)는 바꿀 수 없어요 (수면은 하루 범위 계산에 쓰이므로)
    ({ lifePatternId, ...body }: LifePatternInput & { lifePatternId: number }) =>
      api.put<LifePattern>(`/v1/life-patterns/${lifePatternId}`, body),
    '고정 시간을 수정했어요.'
  )
}

export function useDeleteLifePattern() {
  return useLifePatternMutation(
    (lifePatternId: number) => api.delete(`/v1/life-patterns/${lifePatternId}`),
    '고정 시간을 삭제했어요.'
  )
}
