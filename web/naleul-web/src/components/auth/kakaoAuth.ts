/**
 * 카카오 로그인 공통 헬퍼 — 로그인 모달(LoginRequiredDialog)과 /login 페이지가 같이 써요.
 * NEXT_PUBLIC_ 환경변수는 빌드 때 코드에 박혀서 브라우저에서도 읽을 수 있어요.
 */

/** 콜백이 실패를 알릴 때 붙이는 쿼리 이름 (예: /goal?loginError=cancelled) */
export const LOGIN_ERROR_PARAM = 'loginError'

export const LOGIN_ERROR_MESSAGES: Record<string, string> = {
  // 취소 문구는 로그인 방식마다 달라요 (cancelled 는 예전 카카오 링크 호환용)
  cancelled: '카카오 로그인이 취소됐어요.',
  kakao_cancelled: '카카오 로그인이 취소됐어요.',
  apple_cancelled: 'Apple 로그인이 취소됐어요.',
  no_code: '로그인 정보를 받지 못했어요. 다시 시도해 주세요.',
  login_failed: '로그인에 실패했어요. 잠시 후 다시 시도해 주세요.',
}

export const loginErrorMessage = (code: string | null | undefined) =>
  code ? (LOGIN_ERROR_MESSAGES[code] ?? LOGIN_ERROR_MESSAGES.login_failed) : null

/**
 * 카카오 인가 URL.
 * state 에 "로그인 후 돌아갈 경로"를 담아요. 카카오가 콜백(/redirect)에 state 를 그대로 돌려줘요.
 */
export function kakaoAuthUrl(next: string) {
  const url = new URL('https://kauth.kakao.com/oauth/authorize')
  url.searchParams.set('client_id', process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID ?? '')
  url.searchParams.set('redirect_uri', process.env.NEXT_PUBLIC_KAKAO_REDIRECT_URI ?? '')
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('state', next)
  return url.toString()
}
