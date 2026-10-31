/**
 * "사용자가 성향을 직접 골랐는지"를 기억해요.
 * 확정 요청의 planningStyleSelectedByUser 에 필요해요. (FE-4)
 *
 * 탭을 닫으면 사라져도 되는 값이라 sessionStorage 를 써요.
 * 시크릿 모드 등에서 저장소가 막혀 있어도 화면이 깨지지 않게 try/catch 로 감싸요.
 */
const key = (sessionId: number) => `naleul:goal-creation:${sessionId}:style-by-user`

export function markStyleSelectedByUser(sessionId: number) {
  try {
    sessionStorage.setItem(key(sessionId), '1')
  } catch {
    /* 저장 못 해도 기능엔 지장 없음 */
  }
}

export function wasStyleSelectedByUser(sessionId: number): boolean {
  try {
    return sessionStorage.getItem(key(sessionId)) === '1'
  } catch {
    return false
  }
}
