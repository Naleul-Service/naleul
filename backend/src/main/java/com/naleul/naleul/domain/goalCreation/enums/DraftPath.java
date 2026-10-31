package com.naleul.naleul.domain.goalCreation.enums;

/**
 * 확정한 세션의 경로 (V68). 한 세션에서 여러 개를 썼으면 가장 강한 신호 하나:
 * REGEN > QUICK_FIX > MANUAL > AS_IS. 성향 · 강도 변경은 조건 변경이라 경로에 넣지 않는다.
 */
public enum DraftPath {
    /** ① 다시 생성 — 진짜 마음에 안 듦 (유일한 실패 신호) */
    REGEN,
    /** ② 말로 고치기 — 손으로 고치기 귀찮아서 AI 에게 부분 수정 */
    QUICK_FIX,
    /** ③ 직접 수정 — 고칠 게 적거나 직접 고르고 싶음 */
    MANUAL,
    /** ④ 그대로 확정 — 확정 뒤 수정 여부를 같이 봐야 함 */
    AS_IS
}
