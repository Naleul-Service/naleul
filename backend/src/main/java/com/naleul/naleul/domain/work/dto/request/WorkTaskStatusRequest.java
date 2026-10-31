package com.naleul.naleul.domain.work.dto.request;

import com.naleul.naleul.domain.work.enums.WorkBoardColumn;
import jakarta.validation.constraints.NotNull;

/** 보드에서 칸 옮기기 (드래그) */
public record WorkTaskStatusRequest(
        @NotNull(message = "옮길 칸을 골라 주세요.")
        WorkBoardColumn status
) {
}
