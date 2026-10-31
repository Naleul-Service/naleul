package com.naleul.naleul.domain.goalCreation.dto.plan;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.naleul.naleul.domain.generalCategory.enums.MetricComparator;
import com.naleul.naleul.domain.generalCategory.enums.MetricPeriod;
import com.naleul.naleul.domain.goalCategory.enums.GoalTopic;
import com.naleul.naleul.domain.goalCategory.kind.GoalSubType;
import com.naleul.naleul.domain.user.enums.PlanningStyle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 명세 4-4 초안 JSON.
 * 같은 구조를 ① AI 출력 ② GET draft 응답의 plan ③ POST confirm 요청 바디에 공통으로 쓴다.
 * DB id가 없으므로 tempId(문자열)로 서로를 참조한다.
 */
public record GoalPlanDto(
        PlanGoal goal,
        List<PlanSubGoal> subGoals,
        List<PlanMilestone> milestones,
        List<PlanTask> tasks,
        List<PlanViolation> warnings,
        PlanFit fit                     // 서버가 채우는 "나에게 맞춘 점" (AI 출력·확정 요청에서는 무시)
) {

    public GoalPlanDto {
        subGoals = subGoals != null ? subGoals : List.of();
        milestones = milestones != null ? milestones : List.of();
        tasks = tasks != null ? tasks : List.of();
        warnings = warnings != null ? warnings : List.of();
    }

    /** fit 없이 (AI 출력 · 기존 호출부) */
    public GoalPlanDto(PlanGoal goal, List<PlanSubGoal> subGoals, List<PlanMilestone> milestones,
                       List<PlanTask> tasks, List<PlanViolation> warnings) {
        this(goal, subGoals, milestones, tasks, warnings, null);
    }

    public GoalPlanDto withWarnings(List<PlanViolation> newWarnings) {
        return new GoalPlanDto(goal, subGoals, milestones, tasks, newWarnings, fit);
    }

    public GoalPlanDto withFit(PlanFit newFit) {
        return new GoalPlanDto(goal, subGoals, milestones, tasks, warnings, newFit);
    }

    /**
     * 개인 맞춤 근거 (초안 화면 "나에게 맞춘 점").
     * @param intensity          STEADY / CHALLENGE
     * @param weeklyCapMinutes   이 사람 기준 루틴 주간 상한
     * @param planWeeklyMinutes  이 계획의 루틴 주간 합계
     * @param basedOnHistory     실행 기록으로 맞췄는지 (false 면 인터뷰 답변 기준)
     * @param notes              근거 문장 (계획을 직접 확인해서 만든 것만)
     */
    public record PlanFit(String intensity, int weeklyCapMinutes, int planWeeklyMinutes,
                          boolean basedOnHistory, List<String> notes) {
    }

    // ── 하위 타입 ─────────────────────────────────────────

    public record PlanGoal(
            String title,
            String emoji,
            GoalTopic category,
            String description,
            PlanGoalMetric metric,          // 수치 없는 목표면 null
            LocalDate startDate,
            LocalDate endDate,
            PlanningStyle planningStyle,
            String aiNote,
            GoalSubType subType             // AI 가 고른 2단계 카테고리 (사용자가 고르지 않았을 때만 저장에 쓰임)
    ) {
        public PlanGoal(String title, String emoji, GoalTopic category, String description, PlanGoalMetric metric,
                        LocalDate startDate, LocalDate endDate, PlanningStyle planningStyle, String aiNote) {
            this(title, emoji, category, description, metric, startDate, endDate, planningStyle, aiNote, null);
        }
    }

    public record PlanGoalMetric(String name, String unit, BigDecimal startValue, BigDecimal targetValue) {
    }

    public record PlanSubGoal(
            String tempId,
            String title,
            String emoji,
            String description,
            PlanSubGoalMetric metric
    ) {
    }

    public record PlanSubGoalMetric(
            String name,
            String unit,
            MetricComparator comparator,
            BigDecimal targetValue,
            MetricPeriod period
    ) {
    }

    public record PlanMilestone(
            String tempId,
            String title,
            String description,
            LocalDate dueDate,
            BigDecimal targetValue          // 수치 없는 목표면 null
    ) {
    }

    /**
     * ROUTINE: routineDays, preferredStartTime, startDate, endDate 사용 (endDate null = 목표 종료일까지)
     * ONE_TIME: milestoneTempId, scheduledDate, dueDate 사용
     */
    public record PlanTask(
            String tempId,
            TaskType type,
            String subGoalTempId,
            String milestoneTempId,
            String title,
            String emoji,
            Integer durationMinutes,
            List<Weekday> routineDays,
            @JsonFormat(pattern = "HH:mm") LocalTime preferredStartTime,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate scheduledDate,
            LocalDate dueDate,
            String description,             // 루틴의 구체적인 방법 (예: "스쿼트 4x10, 런지 3x12…"), 없으면 null
            String reason                   // 이렇게 정한 이유 (예: "초보라 주 3회 30분부터, 근육 회복을 위해 하루씩 쉬어요") — 초안 검토 화면용
    ) {
        public PlanTask(String tempId, TaskType type, String subGoalTempId, String milestoneTempId, String title,
                        String emoji, Integer durationMinutes, List<Weekday> routineDays, LocalTime preferredStartTime,
                        LocalDate startDate, LocalDate endDate, LocalDate scheduledDate, LocalDate dueDate) {
            this(tempId, type, subGoalTempId, milestoneTempId, title, emoji, durationMinutes, routineDays,
                    preferredStartTime, startDate, endDate, scheduledDate, dueDate, null, null);
        }

        public PlanTask(String tempId, TaskType type, String subGoalTempId, String milestoneTempId, String title,
                        String emoji, Integer durationMinutes, List<Weekday> routineDays, LocalTime preferredStartTime,
                        LocalDate startDate, LocalDate endDate, LocalDate scheduledDate, LocalDate dueDate,
                        String description) {
            this(tempId, type, subGoalTempId, milestoneTempId, title, emoji, durationMinutes, routineDays,
                    preferredStartTime, startDate, endDate, scheduledDate, dueDate, description, null);
        }

        // @JsonIgnore: 이게 없으면 Jackson이 isXxx() 메서드를 getter로 보고
        // JSON에 "routine": true, "oneTime": false 를 몰래 끼워 넣는다 (API 응답·DB payload·AI 프롬프트까지 오염)
        @JsonIgnore
        public boolean isRoutine() {
            return type == TaskType.ROUTINE;
        }

        @JsonIgnore
        public boolean isOneTime() {
            return type == TaskType.ONE_TIME;
        }
    }
}
