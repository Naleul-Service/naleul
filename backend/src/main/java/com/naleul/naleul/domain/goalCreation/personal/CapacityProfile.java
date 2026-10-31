package com.naleul.naleul.domain.goalCreation.personal;

import com.naleul.naleul.domain.pattern.calculator.HeatmapStats.TimeWindow;

import java.time.DayOfWeek;

/**
 * 계획을 짜기 전에 보는 "이 사람의 실행 역량" (최근 4주, 모든 목표 합산).
 * 나의 패턴 계산기(PatternCalculator)의 결과를 계획에 필요한 값만 추려 담는다.
 *
 * @param evaluatedTasks             최근 4주 평가 대상 Task 수 (계획한 날이 지난 Task)
 * @param executionRate              실행률 % (표본이 부족하면 null)
 * @param weeklyDoneMinutes          일주일에 실제로 끝낸 시간(분) 평균
 * @param activeRoutineCount         지금 진행 중인 루틴 수 (모든 목표)
 * @param activeRoutineWeeklyMinutes 지금 진행 중인 루틴의 주간 합계(분)
 * @param golden                     가장 잘 실행하는 시간대 (없으면 null)
 * @param weak                       자주 놓치는 시간대 (없으면 null)
 * @param weakDay                    평균보다 5%p 이상 낮은 요일 (없으면 null)
 * @param weakDayRate                그 요일 실행률
 * @param averageDayRate             요일 평균 실행률
 */
public record CapacityProfile(
        int evaluatedTasks,
        Integer executionRate,
        int weeklyDoneMinutes,
        int activeRoutineCount,
        int activeRoutineWeeklyMinutes,
        TimeWindow golden,
        TimeWindow weak,
        DayOfWeek weakDay,
        Integer weakDayRate,
        Integer averageDayRate
) {
    /** 이만큼은 있어야 "기록 기반"으로 계획한다. 부족하면 인터뷰에서 들은 현재 수준을 출발점으로 */
    public static final int MIN_TASKS_FOR_HISTORY = 10;

    public boolean hasHistory() {
        return evaluatedTasks >= MIN_TASKS_FOR_HISTORY && executionRate != null;
    }

    /** 기록이 전혀 없는 신규 사용자 */
    public static CapacityProfile empty() {
        return new CapacityProfile(0, null, 0, 0, 0, null, null, null, null, null);
    }
}
