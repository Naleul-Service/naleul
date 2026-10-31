/**
 * 로그인 후 이동할 경로 검증.
 * "/goal" 같은 우리 사이트 내부 경로만 허용해요.
 * "//evil.com" 이나 "https://evil.com" 을 허용하면 로그인 직후 피싱 사이트로 보내는 공격(Open Redirect)이 가능해져요.
 */
export function safeRedirectPath(value: string | null | undefined): string {
  if (!value) return '/'
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return '/'
  if (value.startsWith('/login') || value.startsWith('/api')) return '/'
  return value
}
