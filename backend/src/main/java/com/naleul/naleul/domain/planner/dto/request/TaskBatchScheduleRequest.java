package com.naleul.naleul.domain.planner.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 여러 Task 를 한 번에 옮기기 (연속으로 쪼개 둔 Task 묶음 드래그).
 * { "items": [ { "taskId": 1, "plannedStartAt": "2026-11-18T21:00:00", "plannedEndAt": "2026-11-18T21:30:00" }, ... ] }
 * 전부 저장되거나, 하나라도 자리가 없으면 아무것도 바뀌지 않는다.
 */
public record TaskBatchScheduleRequest(
        @NotEmpty(message = "옮길 Task 가 없습니다.")
        @Size(max = 20, message = "한 번에 20개까지 옮길 수 있습니다.")
        List<@Valid Item> items
) {
    public record Item(
            @NotNull(message = "Task id 는 필수입니다.")
            Long taskId,

            @NotNull(message = "시작 시간은 필수입니다.")
            LocalDateTime plannedStartAt,

            @NotNull(message = "종료 시간은 필수입니다.")
            LocalDateTime plannedEndAt
    ) {
    }
}
