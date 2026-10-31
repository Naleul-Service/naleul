/**
 * 홈은 넓은 화면에서 스크롤 없이 한 화면에 다 보이게 칸을 나눠요.
 * 카드는 칸 높이를 채우고(FILL), 내용이 넘치면 카드 안에서만 스크롤돼요(BODY).
 * 좁은 화면에선 칸 높이가 없어서 그냥 내용만큼 늘어나요.
 */
export const FILL = 'flex min-h-0 flex-col p-4 sm:p-5'
export const BODY = 'min-h-0 flex-1 overflow-y-auto -mx-1 px-1'
