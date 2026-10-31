/**
 * 사업자 정보 — 전자상거래법 · PG(토스페이먼츠 · 토스페이) 심사로 결제가 있는 사이트에 표시해야 해요.
 * 공개 페이지 바닥 · 멤버십 화면 · 이용약관 부칙에 같은 값을 써요. (iOS · Android 이용약관도 같은 값)
 * 비어 있는 값은 표시하지 않아요 (통신판매업 신고번호는 비어 있으면 "신고 진행 중").
 */
export const BUSINESS = {
  name: '나를',
  ceo: '황유림',
  regNo: '495-02-03664',
  address: '경기도 이천시 애련정로 180, 202동 1203호(증포동, 설봉2차 푸르지오)',
  phone: '010-7557-9217',
  mailOrderNo: '제2026-경기이천-0730호',
  email: 'hske3602@naver.com',
} as const

/** 한 줄씩 — "상호 나를 · 대표 황유림" 처럼 이어 붙여 보여줘요 */
export function businessLines(): string[] {
  const b = BUSINESS
  return [
    `상호 ${b.name} · 대표 ${b.ceo} · 사업자등록번호 ${b.regNo}`,
    `통신판매업 신고 ${b.mailOrderNo || '신고 진행 중'}`,
    `주소 ${b.address}`,
    [b.phone && `전화 ${b.phone}`, `이메일 ${b.email}`].filter(Boolean).join(' · '),
  ]
}
