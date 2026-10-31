package com.naleul.naleul.domain.pattern.calculator;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 평가 대상 Task 한 건 (명세 4 · 1-1). DB 엔티티가 아니라 계산용 값 객체.
 *
 * 실제 시각(actualStart/End)이 없는 완료 Task 는 "계획한 시각에 했다"고 본다 (명세 4 v0.2).
 */
public record PatternTaskSample(
        Long taskId,
        Long routineId, String routineName, String routineEmoji,
        Long goalId, String goalName, String goalEmoji, String goalColorCode,
        boolean goalTemporary, LocalDate goalStartDate,
        String taskName, String taskEmoji,
        boolean completed,
        LocalDateTime plannedStart, LocalDateTime plannedEnd, Integer plannedDurationMinutes,
        LocalDate date,
        LocalDate dueDate,
        LocalDateTime completedAt,
        int carryOverCount,
        LocalDateTime actualStart, LocalDateTime actualEnd, Integer actualMinutes
) {

    public boolean isRoutine() {
        return routineId != null;
    }

    /** TimeBlock = 시간이 있는 Task */
    public boolean timed() {
        return plannedStart != null;
    }

    public DayOfWeek dayOfWeek() {
        return date.getDayOfWeek();
    }

    public int startHour() {
        return plannedStart.getHour();
    }

    /** 계획 소요 시간(분). 모르면 0 */
    public int plannedMinutes() {
        if (plannedDurationMinutes != null && plannedDurationMinutes > 0) return plannedDurationMinutes;
        if (plannedStart != null && plannedEnd != null && plannedEnd.isAfter(plannedStart)) {
            return (int) Duration.between(plannedStart, plannedEnd).toMinutes();
        }
        return 0;
    }

    /** 실제로 쓴 시간(분). 완료 Task 만 — 기록이 없으면 계획 시간 */
    public int spentMinutes() {
        if (!completed) return 0;
        if (actualMinutes != null && actualMinutes > 0) return actualMinutes;
        if (actualStart != null && actualEnd != null && actualEnd.isAfter(actualStart)) {
            return (int) Duration.between(actualStart, actualEnd).toMinutes();
        }
        return plannedMinutes();
    }

    /**
     * 시작 지연(분) = 실제 시작 − 계획 시작. 완료된 TimeBlock 만 값이 있다.
     * 실제 시각 기록이 없으면 계획대로 시작했다고 보고 0.
     */
    public Integer startDelayMinutes() {
        if (!completed || plannedStart == null) return null;
        if (actualStart == null) return 0;
        return (int) Duration.between(plannedStart, actualStart).toMinutes();
    }

    /** 화면에 보여줄 이름: 루틴이면 루틴 이름 */
    public String displayName() {
        return routineName != null ? routineName : taskName;
    }

    public String displayEmoji() {
        if (routineEmoji != null) return routineEmoji;
        if (taskEmoji != null) return taskEmoji;
        return goalEmoji;
    }
}
