package com.naleul.naleul.domain.actualTask.dto.request;

import java.time.LocalDateTime;

public record TaskActualCreateRequest(
        Long taskId,              // nullable — Task에 연결 시만
        String taskName,
        Long goalCategoryId,
        Long generalCategoryId,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt
) {}