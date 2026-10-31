package com.naleul.naleul.domain.planner.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Task 를 다른 날로 옮기기 (캘린더 블록 상세 "다른 날로" · 시간 미정 → 다른 날 시간 미정 드래그).
 * { "date": "2026-11-19", "keepTime": true }
 *  - keepTime true  : 같은 시각으로 그날에 놓아 본다. 자리가 없으면 그날 "시간 미정"으로
 *  - keepTime false : 그날 "시간 미정"으로
 */
public record TaskMoveDateRequest(
        @NotNull(message = "옮길 날짜는 필수입니다.")
        LocalDate date,
        Boolean keepTime
) {
    public boolean keepTimeOrDefault() {
        return keepTime == null || keepTime;
    }
}
