package com.naleul.naleul.domain.planner.dto.request;

import com.naleul.naleul.domain.planner.enums.ChangeScope;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 루틴 Task 시간 변경 + 적용 범위.
 * 예) { "plannedStartAt": "2026-10-06T07:30:00", "plannedEndAt": "2026-10-06T08:00:00", "scope": "WEEK" }
 * scope 가 없으면 DAY (이날만 = 기존 PATCH /schedule 과 같음).
 */
public record RoutineScheduleRequest(
        @NotNull(message = "시작 시간은 필수입니다.")
        LocalDateTime plannedStartAt,

        @NotNull(message = "종료 시간은 필수입니다.")
        LocalDateTime plannedEndAt,

        ChangeScope scope
) {
}
