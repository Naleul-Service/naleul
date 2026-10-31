package com.naleul.naleul.domain.planner.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배치 제안 확정. 각 항목은 AI 가 제안한 자리(proposed*)와 최종 자리(start/end).
 * 둘이 같으면 AI 배치로(잠그지 않음), 다르면 사용자가 고친 것으로(📌 잠금) 저장한다.
 */
public record FillApplyRequest(
        @NotEmpty @Size(max = 100)
        List<@Valid Item> items
) {
    public record Item(
            @NotNull Long taskId,
            @NotNull LocalDateTime proposedStart,
            @NotNull LocalDateTime proposedEnd,
            @NotNull LocalDateTime start,
            @NotNull LocalDateTime end,
            String reason
    ) {
        public boolean edited() {
            return !proposedStart.equals(start) || !proposedEnd.equals(end);
        }
    }
}
