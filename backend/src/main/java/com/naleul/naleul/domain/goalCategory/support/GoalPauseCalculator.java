package com.naleul.naleul.domain.goalCategory.support;

import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeCalculator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 진행 중인 목표가 "멈춤"인지 (DB 없이 순수 계산).
 *
 * 왜 필요한가: 사용자가 중간에 손을 놓아도 목표는 종료일까지 "진행 중"으로 남는다.
 * 그래서 끝까지 간 목표와 버려진 목표가 구분되지 않고, 이탈을 알아챌 기회도 없다.
 *
 * 활동 = Task 완료 버튼을 누른 날 · 수치를 기록한 날.
 * 시작일(또는 마지막 활동일)부터 아무 활동 없이
 *  - 생활형: 14일 (GoalOutcomeCalculator.IDLE_PAUSE_DAYS — 목표 결과의 "중간에 멈춤"과 같은 기준)
 *  - 업무형: 28일 (종료일 없이 길게 가는 목표라 4주)
 * 이 지나면 멈춤. 목표 상태를 바꾸지는 않고 화면 표시 · 집계에만 쓴다.
 * 매번 조회할 때 계산하므로, 다시 Task 를 완료하면 바로 멈춤이 풀린다.
 */
public final class GoalPauseCalculator {

    public static final int LIFE_PAUSE_DAYS = GoalOutcomeCalculator.IDLE_PAUSE_DAYS;
    public static final int WORK_PAUSE_DAYS = 28;

    private GoalPauseCalculator() {
    }

    /**
     * @param lastActivity 마지막 활동일 (없으면 null)
     * @return 멈춘 지 며칠째인지 (멈춤이 아니면 null)
     */
    public static Integer pausedDays(GoalCategoryStatus status, boolean temporary, boolean record,
                                     LocalDate startDate, LocalDate lastActivity, LocalDate today) {
        if (status != GoalCategoryStatus.IN_PROGRESS || temporary || today == null) return null;
        LocalDate base = latest(startDate, lastActivity);
        if (base == null || base.isAfter(today)) return null;
        long idle = ChronoUnit.DAYS.between(base, today);
        int threshold = record ? WORK_PAUSE_DAYS : LIFE_PAUSE_DAYS;
        return idle >= threshold ? (int) idle : null;
    }

    static LocalDate latest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
