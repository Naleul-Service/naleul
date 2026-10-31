// domain/mission/dto/response/TodayMissionTaskResponse.java
package com.naleul.naleul.domain.mission.dto.response;

import com.naleul.naleul.domain.task.entity.Task;

public record TodayMissionTaskResponse(
        Long taskId,
        String taskName,
        String taskStatus
) {
    public static TodayMissionTaskResponse from(Task task) {
        return new TodayMissionTaskResponse(
                task.getTaskId(),
                task.getTaskName(),
                task.getTaskStatus().name()
        );
    }
}