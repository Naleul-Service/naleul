package com.naleul.naleul.domain.goalEdit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 예) { "instruction": "운동은 주 2회로 줄이고 종료일을 2주 늦춰줘" } */
public record GoalEditPreviewRequest(
        @NotBlank(message = "어떻게 고칠지 적어 주세요.")
        @Size(max = 200, message = "200자 이하로 적어 주세요.")
        String instruction
) {
}
