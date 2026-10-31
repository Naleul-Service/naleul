package com.naleul.naleul.domain.notification.nudge;

/** 루틴 실천 알림 종류 (푸시 data.kind) */
public enum RoutineNudgeKind {
    /** 어제 이 시간에 했던 루틴을 오늘은 아직 안 했을 때 — 그 시각에 */
    YESTERDAY_TIME,
    /** 실천률이 높은 루틴을 계획 시간에 못 했을 때 — 계획 시간을 바꿔 보자고 */
    RATE_RESCHEDULE
}
