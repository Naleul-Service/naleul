package com.naleul.naleul.domain.goalEdit.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 미리보기에서 사용자가 체크한 변경만 보낸다 */
public record GoalEditApplyRequest(
        @NotEmpty(message = "적용할 변경을 골라 주세요.")
        @Size(max = 20)
        List<@Valid GoalEditOp> ops
) {
}
