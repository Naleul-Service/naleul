/**
 * Apple 로그인(웹) 헬퍼.
 *
 * 웹은 iOS 앱과 달리 Apple Developer 의 "Services ID" 를 client_id 로 써요. (앱은 Bundle ID)
 * 같은 팀 · 같은 Primary App ID 로 묶어 두면 Apple 이 주는 사용자 ID(sub)가 앱과 같아서
 * 앱에서 Apple 로 가입한 사람이 웹에서도 같은 계정으로 로그인돼요.
 *
 * 환경변수가 비어 있으면(Services ID 설정 전) Apple 버튼을 숨겨요.
 */
export const APPLE_CLIENT_ID = process.env.NEXT_PUBLIC_APPLE_CLIENT_ID ?? ''
export const APPLE_REDIRECT_URI = process.env.NEXT_PUBLIC_APPLE_REDIRECT_URI ?? ''
export const isAppleLoginEnabled = () => !!APPLE_CLIENT_ID && !!APPLE_REDIRECT_URI

/**
 * Apple 인가 URL.
 *  - scope 에 name/email 을 넣으면 Apple 은 response_mode=form_post 만 허용해요.
 *    → Apple 이 우리 콜백으로 "POST 폼"을 보내요 (id_token, state, 첫 로그인 땐 user).
 *  - state 에 "로그인 후 돌아갈 경로"를 담아요 (카카오와 같은 방식).
 */
export function appleAuthUrl(next: string) {
  const url = new URL('https://appleid.apple.com/auth/authorize')
  url.searchParams.set('client_id', APPLE_CLIENT_ID)
  url.searchParams.set('redirect_uri', APPLE_REDIRECT_URI)
  url.searchParams.set('response_type', 'code id_token')
  url.searchParams.set('response_mode', 'form_post')
  url.searchParams.set('scope', 'name email')
  url.searchParams.set('state', next)
  return url.toString()
}
