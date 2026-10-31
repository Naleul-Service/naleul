/**
 * Google AdSense 게시자 ID — 공개값이라 코드에 기본값으로 둬요 (환경변수로 바꿀 수 있어요).
 * 루트 layout 이 모든 페이지 <head> 에 AdSense 스크립트 · 확인 메타 태그를 넣고 (사이트 소유권 확인 · 승인 심사),
 * AdSlot 은 광고 단위(<ins>)만 그려요. public/ads.txt 도 같은 ID 예요.
 */
export const ADSENSE_CLIENT = process.env.NEXT_PUBLIC_ADSENSE_CLIENT || 'ca-pub-6171233940934011'
