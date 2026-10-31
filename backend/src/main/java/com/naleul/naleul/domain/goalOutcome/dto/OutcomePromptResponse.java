package com.naleul.naleul.domain.goalOutcome.dto;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.goalOutcome.entity.GoalOutcome;

import java.time.LocalDate;

/**
 * 결과 확인 모달 한 건 — "OO 목표 기한이 지났어요. 목표는 만족했나요?"
 *
 * @param autoResult  서버 자동 판정 (ACHIEVED · PARTIAL · NOT_ACHIEVED)
 * @param autoReason  판정 근거 (METRIC · ROUTINE_RATE · TASK_RATE · NO_RECORD)
 * @param autoMessage 화면에 그대로 보여줄 안내 문구 — 예) "마지막 기록이 목표 수치의 86%예요. 80% 이상이라 자동으로 달성 처리했어요."
 */
public record OutcomePromptResponse(
        Long goalCategoryId,
        String goalName,
        String emoji,
        String colorCode,
        LocalDate startDate,
        LocalDate endDate,
        String autoResult,
        String autoReason,
        String autoMessage,
        Integer metricPercent,
        Integer routineRate,
        Integer taskRate,
        boolean aiGenerated
) {
    public static OutcomePromptResponse of(GoalOutcome o) {
        GoalCategory g = o.getGoalCategory();
        return new OutcomePromptResponse(
                g.getGoalCategoryId(), g.getGoalCategoryName(), g.getEmoji(),
                g.getUserColor() != null ? g.getUserColor().getColorCode() : null,
                g.getGoalCategoryStartDate(), g.getGoalCategoryEndDate(),
                o.getAutoResult() != null ? o.getAutoResult().name() : o.getResult().name(),
                o.getAutoReason() != null ? o.getAutoReason().name() : null,
                o.autoMessage(),
                o.getMetricPercent(), o.getRoutineRate(), o.getTaskRate(), o.isAiGenerated());
    }
}
