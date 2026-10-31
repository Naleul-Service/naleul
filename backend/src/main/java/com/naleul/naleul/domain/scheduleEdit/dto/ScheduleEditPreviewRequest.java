package com.naleul.naleul.domain.scheduleEdit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * { "instruction": "겹치는 루틴 정리해줘", "startDate": "2026-10-12", "endDate": "2026-10-18" }
 * startDate ~ endDate: 지금 캘린더에 보이는 기간 (최대 7일) — 이 기간의 Task 를 AI 에 보여줘요. 루틴은 전부 보여줘요.
 */
public record ScheduleEditPreviewRequest(
        @NotBlank(message = "어떻게 바꿀지 적어 주세요.")
        @Size(max = 300, message = "300자 이하로 적어 주세요.")
        String instruction,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
