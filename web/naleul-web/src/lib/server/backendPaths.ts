/**
 * 백엔드 경로를 한 곳에 모아둔 파일.
 * ⚠️ 로그인/재발급 경로는 백엔드 AuthController 기준으로 확인이 필요해요.
 */
export const BACKEND_PATHS = {
  kakaoLogin: '/api/v1/auth/kakao/callback',
  appleLogin: '/api/v1/auth/apple/login',
  tokenReissue: '/api/v1/auth/token-reissue',
  logout: '/api/v1/auth/logout',
  guest: '/api/v1/auth/guest',
} as const

export function backendUrl(path: string): string {
  const base = process.env.API_BASE_URL
  if (!base) throw new Error('환경변수 API_BASE_URL 이 설정되지 않았어요.')
  return `${base.replace(/\/+$/, '')}${path}`
}
