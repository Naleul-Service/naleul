package com.naleul.naleul.domain.habit.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 습관 순서 바꾸기 — 화면 위에서부터의 habitId 순서. 빠진 습관은 그 뒤에 원래 순서대로 */
public record HabitOrderRequest(
        @NotEmpty(message = "순서를 정할 습관을 보내 주세요.")
        List<Long> habitIds
) {
}
