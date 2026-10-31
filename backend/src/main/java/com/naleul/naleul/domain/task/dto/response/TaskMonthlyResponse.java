package com.naleul.naleul.domain.task.dto.response;

import java.util.List;
import java.util.Map;

public record TaskMonthlyResponse(
        Map<String, List<TaskResponse>> tasksByDate  // { "2024-01-01": [...], "2024-01-02": [] }
) {
    public static TaskMonthlyResponse from(Map<String, List<TaskResponse>> tasksByDate) {
        return new TaskMonthlyResponse(tasksByDate);
    }
}