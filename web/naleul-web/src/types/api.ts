/**
 * 백엔드 공통 응답 형식 (Spring ApiResponse)
 * { success, status: "OK" | "CONFLICT" ..., message, data }
 *
 * - status 는 HTTP 상태 "이름" 문자열이에요. 숫자 상태코드는 따로 httpStatus 로 다뤄요.
 * - null 필드는 백엔드에서 생략되므로 전부 optional 이에요.
 */
export interface ApiEnvelope<T = unknown> {
  success: boolean
  status?: string
  message?: string
  data?: T
}

/** 확정 API 422 응답의 data 구조 */
export interface Violation {
  ruleId: string
  path: string
  message: string
}
