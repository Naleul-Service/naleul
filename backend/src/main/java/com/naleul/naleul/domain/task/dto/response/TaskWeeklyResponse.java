package com.naleul.naleul.domain.task.dto.response;

import java.util.List;
import java.util.Map;

public record TaskWeeklyResponse(
        Map<String, List<TaskResponse>> tasksByDay  // { "MON": [...], "TUE": [...] }
) {
    public static TaskWeeklyResponse from(Map<String, List<TaskResponse>> tasksByDay) {
        return new TaskWeeklyResponse(tasksByDay);
    }
}