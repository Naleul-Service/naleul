package com.naleul.naleul.domain.goalCreation.client;

import com.naleul.naleul.domain.goalCreation.personal.PersonalPlanContext;
import com.naleul.naleul.domain.goalCategory.kind.GoalKind;
import com.naleul.naleul.domain.goalCreation.dto.plan.GoalPlanDto;
import com.naleul.naleul.domain.goalCreation.dto.plan.PlanViolation;
import com.naleul.naleul.domain.goalCreation.dto.slot.GoalSlots;
import com.naleul.naleul.domain.user.enums.PlanningStyle;

import java.time.LocalDate;
import java.util.List;

/**
 * 계획 AI 입력 (명세 8-3).
 * @param previousPlan     다시 생성 또는 HARD 위반 수정 요청일 때 이전 초안 (첫 생성이면 null)
 * @param feedback         "다시 생성" 시 사용자 피드백 (최대 200자, 없으면 null)
 * @param violationsToFix  HARD 위반 수정 요청일 때 위반 목록 (아니면 빈 리스트)
 * @param sourceContext    임시 목표 구체화일 때 기존 Task 목록 등 (없으면 null)
 * @param kind             사용자가 고른 목표 카테고리 (없으면 null → AI 가 goal.subType 으로 분류)
 * @param userAnswers      인터뷰에서 사용자가 한 말 원문 (슬롯에 안 담기는 "헬스장 다녀요, 무릎이 안 좋아요" 같은 정보)
 * @param mustDo           시작 화면에서 고른 "꼭 하고 싶은 일" (V42) — 계획에 반드시 들어가야 한다
 * @param personal         개인 맞춤 정보 — 최근 실행 역량 · 강도 · 개인 상한 · 달성자 집계 (V47, 없으면 null)
 *                         있으면 maxWeeklyRoutineMinutes 는 개인 상한과 같다
 */
public record PlanGenerationRequest(
        GoalSlots slots,
        PlanningStyle planningStyle,
        LocalDate today,
        int maxWeeklyRoutineMinutes,
        GoalPlanDto previousPlan,
        String feedback,
        List<PlanViolation> violationsToFix,
        String sourceContext,
        GoalKind kind,
        List<String> userAnswers,
        List<String> mustDo,
        PersonalPlanContext personal
) {
    public PlanGenerationRequest {
        violationsToFix = violationsToFix != null ? violationsToFix : List.of();
        userAnswers = userAnswers != null ? userAnswers : List.of();
        mustDo = mustDo != null ? mustDo : List.of();
    }

    public PlanGenerationRequest(GoalSlots slots, PlanningStyle planningStyle, LocalDate today, int maxWeeklyRoutineMinutes,
                                 GoalPlanDto previousPlan, String feedback, List<PlanViolation> violationsToFix,
                                 String sourceContext, GoalKind kind, List<String> userAnswers, List<String> mustDo) {
        this(slots, planningStyle, today, maxWeeklyRoutineMinutes, previousPlan, feedback, violationsToFix,
                sourceContext, kind, userAnswers, mustDo, null);
    }

    public PlanGenerationRequest(GoalSlots slots, PlanningStyle planningStyle, LocalDate today, int maxWeeklyRoutineMinutes,
                                 GoalPlanDto previousPlan, String feedback, List<PlanViolation> violationsToFix,
                                 String sourceContext, GoalKind kind, List<String> userAnswers) {
        this(slots, planningStyle, today, maxWeeklyRoutineMinutes, previousPlan, feedback, violationsToFix,
                sourceContext, kind, userAnswers, List.of(), null);
    }

    public PlanGenerationRequest(GoalSlots slots, PlanningStyle planningStyle, LocalDate today, int maxWeeklyRoutineMinutes,
                                 GoalPlanDto previousPlan, String feedback, List<PlanViolation> violationsToFix,
                                 String sourceContext) {
        this(slots, planningStyle, today, maxWeeklyRoutineMinutes, previousPlan, feedback, violationsToFix,
                sourceContext, null, List.of(), List.of(), null);
    }

    /** 위반 수정 요청용 요청 객체 만들기 */
    public PlanGenerationRequest forRevision(GoalPlanDto invalidPlan, List<PlanViolation> violations) {
        return new PlanGenerationRequest(slots, planningStyle, today, maxWeeklyRoutineMinutes,
                invalidPlan, feedback, violations, sourceContext, kind, userAnswers, mustDo, personal);
    }
}
