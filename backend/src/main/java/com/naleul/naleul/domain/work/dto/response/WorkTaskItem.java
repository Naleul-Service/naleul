package com.naleul.naleul.domain.work.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.work.enums.WorkBoardColumn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * 업무 보드의 Task 한 줄 (V52)
 *
 * @param date      하기로 한 날 (null = "할 일 목록", 날짜 미정)
 * @param startTime 시간이 정해졌으면 시작 시각
 * @param dueInDays 마감까지 남은 날 (오늘 마감 0, 지났으면 음수, 마감 없으면 null)
 * @param overdue   마감이 지났는데 아직 안 끝남
 * @param routine   반복 루틴의 그날 Task (보드 칸에는 안 나오고 "오늘 할 일"에만)
 */
public record WorkTaskItem(
        Long taskId,
        String taskName,
        String emoji,
        Long topicId,
        String topicName,
        WorkBoardColumn status,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        LocalDate dueDate,
        Integer dueInDays,
        boolean overdue,
        String memo,
        boolean routine,
        LocalDateTime completedAt
) {
    public static WorkTaskItem from(Task t, LocalDate today) {
        WorkBoardColumn column = t.boardColumn();
        Integer dueIn = t.getDueDate() == null ? null : (int) ChronoUnit.DAYS.between(today, t.getDueDate());
        return new WorkTaskItem(
                t.getTaskId(),
                t.getTaskName(),
                t.getEmoji(),
                t.getWorkTopic() != null ? t.getWorkTopic().getWorkTopicId() : null,
                t.getWorkTopic() != null ? t.getWorkTopic().getTopicName() : null,
                column,
                t.plannedDate(),
                t.getPlannedStartAt() != null ? t.getPlannedStartAt().toLocalTime() : null,
                t.getPlannedEndAt() != null ? t.getPlannedEndAt().toLocalTime() : null,
                t.getDueDate(),
                dueIn,
                dueIn != null && dueIn < 0 && column != WorkBoardColumn.DONE,
                t.getWorkMemo(),
                t.isRoutineTask(),
                t.getCompletedAt());
    }
}
