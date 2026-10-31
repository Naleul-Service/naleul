package com.naleul.naleul.domain.goalProgress.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 목표 수치 기록. 예) { "value": 71.4 } → 오늘 몸무게 71.4
 *                    { "date": "2026-10-04", "value": 71.9, "memo": "회식 다음 날" } → 지난 날 기록
 * date 가 없으면 오늘. 미래 날짜는 안 된다. 같은 날짜는 덮어쓴다.
 */
public record GoalMetricLogRequest(
        LocalDate date,

        @NotNull(message = "기록할 값을 입력해주세요.")
        @DecimalMin(value = "-99999999.99", message = "값이 너무 작아요.")
        @DecimalMax(value = "99999999.99", message = "값이 너무 커요.")
        BigDecimal value,

        @Size(max = 100, message = "메모는 100자 이하로 입력해주세요.")
        String memo
) {
}
