package com.naleul.naleul.domain.milestone.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 마일스톤(점검 시점) 추가. 예: { "title": "1개월 차", "dueDate": "2026-11-06" }
 * targetValue 를 비우면 목표의 시작값 → 목표값 직선에서 자동으로 계산하고, 보내면 "직접 정한 값"이 된다 (V40).
 */
public record MilestoneCreateRequest(
        @NotBlank(message = "마일스톤 이름은 필수입니다.")
        @Size(max = 40, message = "마일스톤 이름은 40자 이하로 입력해주세요.")
        String title,

        @Size(max = 300, message = "설명은 300자 이하로 입력해주세요.")
        String description,

        @NotNull(message = "기한은 필수입니다.")
        LocalDate dueDate,

        BigDecimal targetValue
) {
}
