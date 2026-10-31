package com.naleul.naleul.domain.goalCreation.validator;

import com.naleul.naleul.domain.goalCreation.personal.PersonalPlanContext;
import com.naleul.naleul.domain.goalCreation.dto.slot.MetricSlot;
import com.naleul.naleul.domain.goalCreation.dto.slot.PracticePreferenceSlot;
import com.naleul.naleul.domain.user.enums.PlanningStyle;

import java.time.LocalDate;

/**
 * 검증에 필요한 "초안 바깥" 정보.
 * @param today                   사용자 기준 오늘 (GoalClock.today())
 * @param planningStyle           배치 규칙(V7) 판단 기준
 * @param preference              사용자가 말한 요일·시간대 (없으면 null → V6-2, V6-3 건너뜀)
 * @param maxWeeklyRoutineMinutes 루틴 주간 합계 상한 (goal.ai.max-weekly-routine-minutes)
 * @param metric                  인터뷰에서 사용자가 말한 현재·목표 수치 (없으면 null → V1-3 의 "수치 누락" 검사 건너뜀)
 * @param strictContent           AI 가 만든 초안 검사인지 — true 면 내용 품질 규칙(V5-6 준비 Task 금지, V5-7 루틴 설명 필수)도 HARD.
 *                                사용자가 고친 초안을 확정할 때(false)는 사용자가 직접 적은 내용을 막지 않는다.
 * @param personal                개인 맞춤 정보 (V47, 없으면 null) — 있으면 V6-1(개인 상한)이 AI 초안에서 HARD,
 *                                V8 규칙(자주 놓치는 시간 · 이미 많은 루틴)을 경고로 본다
 */
public record PlanValidationContext(
        LocalDate today,
        PlanningStyle planningStyle,
        PracticePreferenceSlot preference,
        int maxWeeklyRoutineMinutes,
        MetricSlot metric,
        boolean strictContent,
        PersonalPlanContext personal
) {
    public PlanValidationContext(LocalDate today, PlanningStyle planningStyle, PracticePreferenceSlot preference,
                                 int maxWeeklyRoutineMinutes, MetricSlot metric, boolean strictContent) {
        this(today, planningStyle, preference, maxWeeklyRoutineMinutes, metric, strictContent, null);
    }

    public PlanValidationContext(LocalDate today, PlanningStyle planningStyle,
                                 PracticePreferenceSlot preference, int maxWeeklyRoutineMinutes) {
        this(today, planningStyle, preference, maxWeeklyRoutineMinutes, null, false, null);
    }

    public PlanValidationContext(LocalDate today, PlanningStyle planningStyle,
                                 PracticePreferenceSlot preference, int maxWeeklyRoutineMinutes, MetricSlot metric) {
        this(today, planningStyle, preference, maxWeeklyRoutineMinutes, metric, false, null);
    }
}
