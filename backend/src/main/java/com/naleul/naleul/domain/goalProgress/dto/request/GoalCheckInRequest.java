package com.naleul.naleul.domain.goalProgress.dto.request;

import com.naleul.naleul.domain.goalProgress.enums.ReflectionMood;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 오늘 기록 + 회고를 한 번에.
 * 예) { "value": 64, "mood": "BAD", "note": "점심에 마라탕 먹음" }
 *     { "note": "야근이라 운동 못 함" }   ← 수치 없이 회고만 (수치가 없는 목표도 가능)
 *
 * - date 가 없으면 오늘. 미래 날짜는 안 된다.
 * - value: 수치 목표일 때만 저장 (수치가 없는 목표면 무시). 비우면 그날 수치는 그대로.
 * - mood·note 를 둘 다 비우면 그날 회고를 지운다.
 * - value·mood·note 가 모두 비어 있으면 400.
 */
public record GoalCheckInRequest(
        LocalDate date,

        @DecimalMin(value = "-99999999.99", message = "값이 너무 작아요.")
        @DecimalMax(value = "99999999.99", message = "값이 너무 커요.")
        BigDecimal value,

        ReflectionMood mood,

        @Size(max = 500, message = "회고는 500자 이하로 입력해주세요.")
        String note
) {
}
