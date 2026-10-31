package com.naleul.naleul.domain.scheduleEdit.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 미리보기에서 사용자가 체크한 변경만 보낸다. startDate ~ endDate 는 미리보기 때와 같은 기간
 * (그 기간의 Task 만 옮길 수 있어요 — 미리보기 뒤에 바뀌었으면 400)
 */
public record ScheduleEditApplyRequest(
        @NotEmpty(message = "적용할 변경을 골라 주세요.")
        @Size(max = 30)
        List<@Valid ScheduleEditOp> ops,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
