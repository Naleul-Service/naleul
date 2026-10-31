package com.naleul.naleul.domain.planner.dto.response;

import com.naleul.naleul.domain.goalCategory.entity.GoalCategory;
import com.naleul.naleul.domain.planner.support.DateTimeRange;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * TimeTable 에 그릴 Task 블록 하나 (명세 3 · 5-6).
 * 기존 TaskResponse(iOS 사용 중)는 건드리지 않고 웹 캘린더용으로 따로 만들었다.
 *
 * date    : 이 Task 가 속한 날짜 (시간 있으면 시작 날짜, 없으면 routineDate)
 * missed  : 시간이 지났는데(또는 날짜가 지났는데) 아직 TODO — 화면의 ✕(놓침)
 * locked  : 사용자가 정한 시간 (자동 배치가 옮기지 않음, 🔒)
 * carryPending : 22시 이월로 놓였지만 사용자가 아직 확인 안 함 → 점선 블록 + "옮길까요?" (carriedFromDate = 원래 날짜)
 * actualStartAt / actualEndAt : 완료한 Task 의 실제 수행 시각 (완료 전이거나 기록이 없으면 null).
 *           화면은 완료된 Task 를 이 시각에 그리고, 없으면 계획 시각(planned*)에 그린다.
 *           여러 번 나눠서 했으면 첫 시작 ~ 마지막 끝.
 * actualSegments : 여러 번 나눠서 했을 때만 각 구간 (예: 13~15시, 16~18시). 한 번에 했으면 빈 목록
 * plannedSegments : 자동 배치가 연속된 빈 시간이 없어 나눠 넣었을 때만 계획 구간 (예: 09~12시, 13~14시, V65).
 *           plannedStartAt ~ plannedEndAt 은 첫 시작 ~ 마지막 끝이고, plannedDurationMinutes 는 구간 합계.
 *           화면은 완료 전(또는 계획 기준 보기)에 이 구간마다 블록을 그린다. 한 번에 넣었으면 빈 목록
 */
public record TimeBlockTaskResponse(
        Long taskId,
        String taskName,
        String emoji,
        TaskStatus taskStatus,
        TaskPriority taskPriority,
        TaskKind taskKind,
        TaskSourceType sourceType,
        TaskSource source,
        Long goalCategoryId,
        String goalCategoryName,
        String goalEmoji,
        String goalColorCode,
        boolean goalTemporary,
        Long generalCategoryId,
        String generalCategoryName,
        Long milestoneId,
        String milestoneTitle,
        Long routineId,
        LocalDate date,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        Long plannedDurationMinutes,
        LocalDate scheduledDate,
        LocalDate dueDate,
        boolean locked,
        PlacedBy placedBy,
        String placementReason,
        List<String> placementReasonCodes,
        int carryOverCount,
        LocalDateTime completedAt,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        boolean missed,
        boolean carryPending,
        LocalDate carriedFromDate,
        List<Segment> actualSegments,
        List<Segment> plannedSegments,
        /** 업무형 목표의 세부 목표 (로드맵 · WorkTopic, 2026-10). 없으면 null = "세부 목표 없음" */
        Long workTopicId,
        String workTopicName
) {
    public record Segment(LocalDateTime start, LocalDateTime end) {
    }

    /** 업무 세부 목표 없는 모양 — 테스트 · 다른 화면 응답 호환 */
    public TimeBlockTaskResponse(Long taskId, String taskName, String emoji, TaskStatus taskStatus,
                                 TaskPriority taskPriority, TaskKind taskKind, TaskSourceType sourceType, TaskSource source,
                                 Long goalCategoryId, String goalCategoryName, String goalEmoji, String goalColorCode,
                                 boolean goalTemporary, Long generalCategoryId, String generalCategoryName,
                                 Long milestoneId, String milestoneTitle, Long routineId, LocalDate date,
                                 LocalDateTime plannedStartAt, LocalDateTime plannedEndAt, Long plannedDurationMinutes,
                                 LocalDate scheduledDate, LocalDate dueDate, boolean locked, PlacedBy placedBy,
                                 String placementReason, List<String> placementReasonCodes, int carryOverCount,
                                 LocalDateTime completedAt, LocalDateTime actualStartAt, LocalDateTime actualEndAt,
                                 boolean missed, boolean carryPending, LocalDate carriedFromDate,
                                 List<Segment> actualSegments, List<Segment> plannedSegments) {
        this(taskId, taskName, emoji, taskStatus, taskPriority, taskKind, sourceType, source, goalCategoryId,
                goalCategoryName, goalEmoji, goalColorCode, goalTemporary, generalCategoryId, generalCategoryName,
                milestoneId, milestoneTitle, routineId, date, plannedStartAt, plannedEndAt, plannedDurationMinutes,
                scheduledDate, dueDate, locked, placedBy, placementReason, placementReasonCodes, carryOverCount,
                completedAt, actualStartAt, actualEndAt, missed, carryPending, carriedFromDate, actualSegments,
                plannedSegments, null, null);
    }

    /** V65 이전 모양 (plannedSegments 없음) — 테스트 · 다른 화면 응답 호환 */
    public TimeBlockTaskResponse(Long taskId, String taskName, String emoji, TaskStatus taskStatus,
                                 TaskPriority taskPriority, TaskKind taskKind, TaskSourceType sourceType, TaskSource source,
                                 Long goalCategoryId, String goalCategoryName, String goalEmoji, String goalColorCode,
                                 boolean goalTemporary, Long generalCategoryId, String generalCategoryName,
                                 Long milestoneId, String milestoneTitle, Long routineId, LocalDate date,
                                 LocalDateTime plannedStartAt, LocalDateTime plannedEndAt, Long plannedDurationMinutes,
                                 LocalDate scheduledDate, LocalDate dueDate, boolean locked, PlacedBy placedBy,
                                 String placementReason, List<String> placementReasonCodes, int carryOverCount,
                                 LocalDateTime completedAt, LocalDateTime actualStartAt, LocalDateTime actualEndAt,
                                 boolean missed, boolean carryPending, LocalDate carriedFromDate,
                                 List<Segment> actualSegments) {
        this(taskId, taskName, emoji, taskStatus, taskPriority, taskKind, sourceType, source, goalCategoryId,
                goalCategoryName, goalEmoji, goalColorCode, goalTemporary, generalCategoryId, generalCategoryName,
                milestoneId, milestoneTitle, routineId, date, plannedStartAt, plannedEndAt, plannedDurationMinutes,
                scheduledDate, dueDate, locked, placedBy, placementReason, placementReasonCodes, carryOverCount,
                completedAt, actualStartAt, actualEndAt, missed, carryPending, carriedFromDate, actualSegments, List.of());
    }

    /** 실제 시각을 Task 자체 컬럼(미션)에서만 읽는다. 일반·루틴의 task_actual 까지 보려면 아래 오버로드 사용 */
    public static TimeBlockTaskResponse from(Task t, LocalDateTime now) {
        return from(t, now, ownActualRange(t));
    }

    /** @param actual 실제 수행 구간 (없으면 null). 완료된 Task 일 때만 응답에 담는다 */
    public static TimeBlockTaskResponse from(Task t, LocalDateTime now, DateTimeRange actual) {
        return from(t, now, actual, List.of());
    }

    /** @param segments 여러 번 나눠서 한 구간들 (2개 이상일 때만 의미 있음) */
    public static TimeBlockTaskResponse from(Task t, LocalDateTime now, DateTimeRange actual, List<DateTimeRange> segments) {
        return from(t, now, actual, segments, List.of());
    }

    /** @param planned 자동 배치가 나눠 넣은 계획 구간 (PlannedSegmentStore 가 계획 시각과 맞는지 확인한 것) */
    public static TimeBlockTaskResponse from(Task t, LocalDateTime now, DateTimeRange actual, List<DateTimeRange> segments,
                                             List<DateTimeRange> planned) {
        DateTimeRange shown = t.getTaskStatus() == TaskStatus.COMPLETED ? actual : null;
        GoalCategory g = t.getGoalCategory();
        LocalDate date = t.getPlannedStartAt() != null ? t.getPlannedStartAt().toLocalDate() : t.getRoutineDate();
        return new TimeBlockTaskResponse(
                t.getTaskId(), t.getTaskName(), t.getEmoji(), t.getTaskStatus(), t.getTaskPriority(),
                t.getTaskKind(), sourceTypeOf(t), t.getSource(),
                g != null ? g.getGoalCategoryId() : null,
                g != null ? g.getGoalCategoryName() : null,
                g != null ? g.getEmoji() : null,
                g != null && g.getUserColor() != null ? g.getUserColor().getColorCode() : null,
                g != null && g.isTemporary(),
                t.getGeneralCategory() != null ? t.getGeneralCategory().getGeneralCategoryId() : null,
                t.getGeneralCategory() != null ? t.getGeneralCategory().getGeneralCategoryName() : null,
                t.getMilestone() != null ? t.getMilestone().getMilestoneId() : null,
                t.getMilestone() != null ? t.getMilestone().getTitle() : null,
                t.getRoutine() != null ? t.getRoutine().getRoutineId() : null,
                date,
                t.getPlannedStartAt(), t.getPlannedEndAt(), t.getPlannedDurationMinutes(),
                t.getScheduledDate(), t.getDueDate(),
                t.isScheduleLocked(), t.getPlacedBy(), t.getPlacementReason(),
                t.getPlacementReasonCodes() == null || t.getPlacementReasonCodes().isBlank()
                        ? List.of() : Arrays.asList(t.getPlacementReasonCodes().split(",")),
                t.getCarryOverCount(), t.getCompletedAt(),
                shown != null ? shown.start() : null,
                shown != null ? shown.end() : null,
                isMissed(t, now),
                t.isCarryPending() && t.getTaskStatus() == TaskStatus.TODO,
                t.getCarriedFromDate(),
                shown != null && segments != null && segments.size() > 1
                        ? segments.stream().map(r -> new Segment(r.start(), r.end())).toList()
                        : List.of(),
                planned != null && planned.size() > 1
                        ? planned.stream().map(r -> new Segment(r.start(), r.end())).toList()
                        : List.of(),
                t.getWorkTopic() != null ? t.getWorkTopic().getWorkTopicId() : null,
                t.getWorkTopic() != null ? t.getWorkTopic().getTopicName() : null);
    }

    /** 미션 완료 때 Task 에 직접 저장한 실제 시각 (없거나 잘못된 값이면 null) */
    public static DateTimeRange ownActualRange(Task t) {
        LocalDateTime s = t.getActualStartAt();
        LocalDateTime e = t.getActualEndAt();
        return s != null && e != null && e.isAfter(s) ? new DateTimeRange(s, e) : null;
    }

    static boolean isMissed(Task t, LocalDateTime now) {
        if (t.getTaskStatus() != TaskStatus.TODO) return false;
        if (t.getPlannedEndAt() != null) return t.getPlannedEndAt().isBefore(now);
        if (t.getPlannedStartAt() != null) return t.getPlannedStartAt().isBefore(now);
        return t.getRoutineDate() != null && t.getRoutineDate().isBefore(now.toLocalDate());
    }

    private static TaskSourceType sourceTypeOf(Task t) {
        if (t.getRoutine() != null) return TaskSourceType.ROUTINE;
        if (t.getMissionParticipant() != null) return TaskSourceType.MISSION;
        return TaskSourceType.MANUAL;
    }
}
