package com.naleul.naleul.domain.planner.dto.request;

import com.naleul.naleul.domain.task.enums.TaskPriority;
import jakarta.validation.constraints.NotNull;

/**
 * TimeBlock 상세에서 우선순위만 바꾸기.
 * 예) { "taskPriority": "A" }
 */
public record TaskPriorityRequest(
        @NotNull(message = "우선순위는 필수입니다.")
        TaskPriority taskPriority
) {}
