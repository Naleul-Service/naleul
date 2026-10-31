package com.naleul.naleul.domain.goalCreation.support;

import com.naleul.naleul.domain.goalCreation.dto.plan.Weekday;
import com.naleul.naleul.domain.goalCreation.dto.slot.*;
import com.naleul.naleul.domain.goalCreation.enums.ReviewField;
import com.naleul.naleul.domain.goalCreation.enums.ReviewResult;
import com.naleul.naleul.domain.user.enums.PlanningStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ChatReviewCalculatorTest {

    private static final GoalSlots AI = GoalSlots.empty()
            .withGoalStatement(Slot.filled("몸무게 10kg 감량"))
            .withMetric(Slot.filled(new MetricSlot("체중", new BigDecimal("78.0"), new BigDecimal("68"), "kg")))
            .withDeadline(Slot.filled(new DeadlineSlot(LocalDate.of(2026, 12, 1), false, null)))
            .withPracticePreference(Slot.filled(new PracticePreferenceSlot(
                    List.of(Weekday.WED, Weekday.MON), List.of(new TimeRange(LocalTime.of(19, 0), LocalTime.of(21, 0))),
                    null, null)));

    private static Map<ReviewField, ChatReviewCalculator.Row> byField(List<ChatReviewCalculator.Row> rows) {
        return rows.stream().collect(Collectors.toMap(ChatReviewCalculator.Row::field, r -> r));
    }

    @Test
    @DisplayName("그대로 계획 만들기 — 숫자 표기(78.0 vs 78)·요일 순서가 달라도 같은 값이면 그대로")
    void keptWhenSameValue() {
        GoalSlots now = AI
                .withMetric(Slot.filled(new MetricSlot("체중", new BigDecimal("78"), new BigDecimal("68.00"), "kg")))
                .withPracticePreference(Slot.filled(new PracticePreferenceSlot(
                        List.of(Weekday.MON, Weekday.WED), List.of(new TimeRange(LocalTime.of(19, 0), LocalTime.of(21, 0))),
                        null, null)));
        Map<ReviewField, ChatReviewCalculator.Row> r = byField(ChatReviewCalculator.compare(AI, now, PlanningStyle.PLANNER, null));
        assertThat(r.get(ReviewField.METRIC).result()).isEqualTo(ReviewResult.KEPT);
        assertThat(r.get(ReviewField.PRACTICE_PREFERENCE).result()).isEqualTo(ReviewResult.KEPT);
        assertThat(r.get(ReviewField.GOAL_STATEMENT).result()).isEqualTo(ReviewResult.KEPT);
        assertThat(r).hasSize(7);
    }

    @Test
    @DisplayName("AI 가 채운 값을 바꾸면 고침, AI 가 비운 항목을 채우면 직접 채움, 안 채우면 비운 채로")
    void editedAddedEmpty() {
        GoalSlots now = AI
                .withDeadline(Slot.filled(new DeadlineSlot(LocalDate.of(2026, 12, 31), false, null)))
                .withApproach(Slot.filled("헬스 주 3회 + 식단"));
        Map<ReviewField, ChatReviewCalculator.Row> r = byField(ChatReviewCalculator.compare(AI, now, PlanningStyle.PLANNER, null));
        assertThat(r.get(ReviewField.DEADLINE).result()).isEqualTo(ReviewResult.EDITED);
        assertThat(r.get(ReviewField.APPROACH).aiFilled()).isFalse();
        assertThat(r.get(ReviewField.APPROACH).result()).isEqualTo(ReviewResult.ADDED);
        assertThat(r.get(ReviewField.MOTIVATION).result()).isEqualTo(ReviewResult.EMPTY);
    }

    @Test
    @DisplayName("실행 성향 — 추천과 다른 성향을 골라야 고침 (안 고르거나 같은 걸 고르면 그대로, 추천이 없으면 계획형 기준)")
    void planningStyle() {
        assertThat(byField(ChatReviewCalculator.compare(AI, AI, PlanningStyle.PLANNER, null))
                .get(ReviewField.PLANNING_STYLE).result()).isEqualTo(ReviewResult.KEPT);
        assertThat(byField(ChatReviewCalculator.compare(AI, AI, PlanningStyle.PLANNER, PlanningStyle.PLANNER))
                .get(ReviewField.PLANNING_STYLE).result()).isEqualTo(ReviewResult.KEPT);
        assertThat(byField(ChatReviewCalculator.compare(AI, AI, PlanningStyle.PLANNER, PlanningStyle.SPONTANEOUS))
                .get(ReviewField.PLANNING_STYLE).result()).isEqualTo(ReviewResult.EDITED);
        assertThat(byField(ChatReviewCalculator.compare(AI, AI, null, PlanningStyle.SPONTANEOUS))
                .get(ReviewField.PLANNING_STYLE).result()).isEqualTo(ReviewResult.EDITED);
    }

    @Test
    @DisplayName("AI 가 채운 값을 지우면 고침")
    void clearedCountsAsEdited() {
        GoalSlots now = AI.withMetric(Slot.skipped());
        assertThat(byField(ChatReviewCalculator.compare(AI, now, PlanningStyle.PLANNER, null))
                .get(ReviewField.METRIC).result()).isEqualTo(ReviewResult.EDITED);
    }
}
