package com.naleul.naleul.domain.goalOutcome.entity;

/**
 * 목표가 끝난 시점의 숫자들 (GoalOutcomeCalculator 가 만든다).
 * 비율은 0~100 정수 %, 표본이 없으면 null.
 *
 * @param metricUnrecorded 수치 목표인데 수치를 기록하지 않아서(또는 너무 오래 안 해서) 수치로 판정하지 않았다.
 *                         이때 metricPercent 는 null 이고 실천률로 판정한다.
 * @param longestIdleDays  기간 중 아무 활동(Task 완료 · 수치 기록)도 없던 가장 긴 구간 (일). 계산할 수 없으면 null.
 *                         GoalOutcomeCalculator.IDLE_PAUSE_DAYS 이상이면 "중간에 멈췄던 목표" → 완주율 계산에 쓴다.
 */
public record OutcomeSnapshot(
        Integer plannedDays,
        Integer actualDays,
        Integer metricPercent,
        Integer routineRate,
        Integer taskRate,
        int routineCount,
        int keptRoutineCount,
        int weeklyRoutineMinutes,
        boolean metricUnrecorded,
        Integer longestIdleDays
) {

    /** 수치 기록 · 활동 날짜를 모를 때 (예전 호출부 호환) */
    public OutcomeSnapshot(Integer plannedDays, Integer actualDays, Integer metricPercent, Integer routineRate,
                           Integer taskRate, int routineCount, int keptRoutineCount, int weeklyRoutineMinutes) {
        this(plannedDays, actualDays, metricPercent, routineRate, taskRate, routineCount, keptRoutineCount,
                weeklyRoutineMinutes, false, null);
    }
}
