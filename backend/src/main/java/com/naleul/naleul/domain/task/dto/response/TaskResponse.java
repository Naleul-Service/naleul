package com.naleul.naleul.domain.task.dto.response;

import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import com.naleul.naleul.domain.task.enums.TaskPriority;
import com.naleul.naleul.domain.task.enums.TaskSourceType; // ✅ import 추가
import com.naleul.naleul.domain.task.enums.TaskStatus;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
        Long taskId,
        String taskName,
        TaskPriority taskPriority,
        Long goalCategoryId,
        String goalCategoryName,
        String goalCategoryColorCode,
        Long generalCategoryId,
        String generalCategoryName,
        String generalCategoryColorCode,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        Long plannedDurationMinutes,
        boolean defaultSettingStatus,
        TaskActualResponse actual,
        Long routineId,
        LocalDate routineDate,
        Long missionId,                          // ✅ 추가
        TaskSourceType sourceType,
        boolean notificationEnabled,
        TaskStatus taskStatus,
        LocalDateTime missionActualStartAt,
        LocalDateTime missionActualEndAt,
        Long missionActualDurationMinutes
) {
    public static TaskResponse from(Task task) {

        String goalColorCode = task.getGoalCategory() != null && task.getGoalCategory().getUserColor() != null
                ? task.getGoalCategory().getUserColor().getColorCode()
                : null;

        String generalColorCode = task.getGeneralCategory() != null && task.getGeneralCategory().getColor() != null
                ? task.getGeneralCategory().getColor().getColorCode()
                : null;

        TaskActualResponse actual = task.getTaskActuals().stream()
                .filter(a -> a.getActualStartAt() != null && a.getActualEndAt() != null)
                .findFirst()
                .map(TaskActualResponse::from)
                .orElse(null);

        Long missionDuration = (task.getActualStartAt() != null && task.getActualEndAt() != null)
                ? Duration.between(task.getActualStartAt(), task.getActualEndAt()).toMinutes()
                : null;

        // ✅ 미션 ID 추출
        Long missionId = task.getMissionParticipant() != null
                ? task.getMissionParticipant().getMission().getMissionId()
                : null;

        return new TaskResponse(
                task.getTaskId(),
                task.getTaskName(),
                task.getTaskPriority(),
                task.getGoalCategory() != null ? task.getGoalCategory().getGoalCategoryId() : null,
                task.getGoalCategory() != null ? task.getGoalCategory().getGoalCategoryName() : null,
                goalColorCode,
                task.getGeneralCategory() != null ? task.getGeneralCategory().getGeneralCategoryId() : null,
                task.getGeneralCategory() != null ? task.getGeneralCategory().getGeneralCategoryName() : null,
                generalColorCode,
                task.getPlannedStartAt(),
                task.getPlannedEndAt(),
                task.getPlannedDurationMinutes(),
                task.isDefaultSettingStatus(),
                actual,
                task.getRoutine() != null ? task.getRoutine().getRoutineId() : null,
                task.getRoutineDate(),
                missionId,                         // ✅ 추가
                resolveSourceType(task),
                task.isNotificationEnabled(),
                task.getTaskStatus(),
                task.getActualStartAt(),
                task.getActualEndAt(),
                missionDuration
        );
    }

    private static TaskSourceType resolveSourceType(Task task) {
        if (task.getRoutine() != null) {
            return TaskSourceType.ROUTINE;
        }
        if (task.getMissionParticipant() != null) {
            return TaskSourceType.MISSION;
        }
        return TaskSourceType.MANUAL;
    }

    public record TaskActualResponse(
            Long taskActualId,
            LocalDateTime actualStartAt,
            LocalDateTime actualEndAt,
            Long actualDurationMinutes
    ) {
        public static TaskActualResponse from(TaskActual actual) {
            return new TaskActualResponse(
                    actual.getTaskActualId(),
                    actual.getActualStartAt(),
                    actual.getActualEndAt(),
                    actual.getActualDurationMinutes()
            );
        }
    }
}