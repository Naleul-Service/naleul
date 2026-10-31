package com.naleul.naleul.domain.planner.engine;

/**
 * 배치 이유 코드 (명세 6-5). task.placement_reason_codes 에 쉼표로 저장된다.
 * 문장 조립은 ReasonComposer 가 한다.
 */
public enum ReasonCode {
    // 왜 먼저 배치했는지 (중요도)
    OVERDUE,
    DEADLINE_TODAY,
    DEADLINE_SOON,
    CARRIED_OVER,
    APPOINTMENT,
    MILESTONE_SOON,

    // 왜 이 시간인지 (슬롯)
    HIGH_EFFICIENCY,   // 그날 평균보다 효율이 확실히 높은 시간대
    BEST_REMAINING,    // 좋은 시간대가 이미 차서, 남은 빈 시간 중 가장 나은 곳
    EARLY_SLOT,        // 보통 중요도 — 일찍 끝낼 수 있게 이른 시간
    LOW_LOAD_SLOT,
    PREFERRED_TIME,
    DEFAULT_CURVE,
    ONLY_SLOT,

    // 내 기록 근거 (이유 문장 두 번째 줄)
    GOAL_TIME_FIT,     // 같은 목표의 일을 이 시간대에 잘 지켜 왔음
    MOVE_HABIT,        // 평소 블록을 이 시간대로 자주 옮겨 왔음
    // 기타
    DURATION_ADJUSTED,
    GOAL_GROUPED,      // 같은 목표의 일과 이어지게 붙여 둠 (2026-10)
    SPLIT_BLOCK,       // 연속된 빈 시간이 없어 고정 시간 앞뒤로 나눠 넣음 (2026-10)
    MOVED_TO_OTHER_DAY
}
