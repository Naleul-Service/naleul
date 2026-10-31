package com.naleul.naleul.domain.planner.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 정해진 Task 들만 배치 미리보기 (Brain dump 로 방금 만든 Task 등) */
public record FillPreviewTasksRequest(
        @NotEmpty @Size(max = 100) List<@NotNull Long> taskIds
) {
}
