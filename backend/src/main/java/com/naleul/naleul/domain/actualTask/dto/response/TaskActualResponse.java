package com.naleul.naleul.domain.actualTask.dto.response;

import com.naleul.naleul.domain.actualTask.entity.TaskActual;
import com.naleul.naleul.domain.task.entity.Task;
import com.naleul.naleul.domain.task.enums.TaskSourceType;

import java.time.LocalDateTime;

// TaskActualResponse.java
public record TaskActualResponse(
        Long taskActualId,
        Long taskId,              // nullable
        String taskName,
        Long goalCategoryId,
        String goalCategoryName,
        String goalCategoryColorCode,
        Long generalCategoryId,
        String generalCategoryName,
        String generalCategoryColorCode,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        Long actualDurationMinutes,
        TaskSourceType sourceType   // ✅ 추가
) {
    public static TaskActualResponse from(TaskActual actual) {
        String goalColorCode = actual.getGoalCategory().getUserColor() != null
                ? actual.getGoalCategory().getUserColor().getColorCode()
                : null;
        String generalColorCode = actual.getGeneralCategory().getColor() != null
                ? actual.getGeneralCategory().getColor().getColorCode()
                : null;

        return new TaskActualResponse(
                actual.getTaskActualId(),
                actual.getTask() != null ? actual.getTask().getTaskId() : null,
                actual.getTaskName(),
                actual.getGoalCategory().getGoalCategoryId(),
                actual.getGoalCategory().getGoalCategoryName(),
                goalColorCode,
                actual.getGeneralCategory().getGeneralCategoryId(),
                actual.getGeneralCategory().getGeneralCategoryName(),
                generalColorCode,
                actual.getActualStartAt(),
                actual.getActualEndAt(),
                actual.getActualDurationMinutes(),
                resolveSourceType(actual)   // ✅ 추가
        );
    }

    /**
     * TaskActual이 연결된 Task를 통해 어느 경로에서 왔는지 판별.
     * task가 null이면(독립 생성) MANUAL, task는 있는데 routine/missionParticipant가
     * 둘 다 없어도 MANUAL. routine이 있으면 ROUTINE, missionParticipant가 있으면 MISSION.
     */
    private static TaskSourceType resolveSourceType(TaskActual actual) {
        Task task = actual.getTask();
        if (task == null) {
            return TaskSourceType.MANUAL;
        }
        if (task.getRoutine() != null) {
            return TaskSourceType.ROUTINE;
        }
        if (task.getMissionParticipant() != null) {
            return TaskSourceType.MISSION;
        }
        return TaskSourceType.MANUAL;
    }
}