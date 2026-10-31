// TaskActualWeeklyResponse.java
package com.naleul.naleul.domain.actualTask.dto.response;

import java.util.List;
import java.util.Map;

public record TaskActualWeeklyResponse(
        Map<String, List<TaskActualResponse>> actualsByDay
) {
    public static TaskActualWeeklyResponse from(Map<String, List<TaskActualResponse>> actualsByDay) {
        return new TaskActualWeeklyResponse(actualsByDay);
    }
}