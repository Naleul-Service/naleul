package com.naleul.naleul.domain.task.dto.request;

import com.naleul.naleul.domain.task.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record TaskCreateRequest(

        @NotBlank(message = "태스크 이름은 필수입니다.")
        String taskName,

        @NotNull(message = "우선순위는 필수입니다.")
        TaskPriority taskPriority,

        @NotNull(message = "목표 카테고리는 필수입니다.")
        Long goalCategoryId,

        @NotNull(message = "일반 카테고리는 필수입니다.")
        Long generalCategoryId,

        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,

        boolean defaultSettingStatus,
        Boolean notificationEnabled
) {}