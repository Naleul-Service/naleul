package com.naleul.naleul.domain.task.dto.request;

import java.time.LocalDateTime;

public record MissionTaskScheduleRequest(
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt
) {}