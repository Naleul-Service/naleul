package com.naleul.naleul.domain.planner.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 드래그로 Task 시간 변경 (한국 시간).  { "plannedStartAt": "2026-11-18T21:30:00", "plannedEndAt": "2026-11-18T22:30:00" }
 * 저장하면 잠긴다(🔒). 고정 시간 위에 놓아도 저장되고 Task 가 그 시간을 덮는다.
 */
public record TaskScheduleRequest(
        @NotNull(message = "시작 시간은 필수입니다.")
        LocalDateTime plannedStartAt,

        @NotNull(message = "종료 시간은 필수입니다.")
        LocalDateTime plannedEndAt
) {
}
