package com.naleul.naleul.domain.actualTask.dto.request;

import java.time.LocalDateTime;

public record TaskActualUpdateRequest(
        String taskName,
        Long goalCategoryId,
        Long generalCategoryId,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt
) {}