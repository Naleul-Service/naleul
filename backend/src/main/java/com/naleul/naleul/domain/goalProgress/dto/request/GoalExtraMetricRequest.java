package com.naleul.naleul.domain.goalProgress.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 추가 그래프 만들기 · 고치기 (V52)
 * 예) { "name": "기출 회독", "unit": "회", "startValue": 0, "targetValue": 5 }
 * 만들 때는 name · startValue · targetValue 필수, 고칠 때는 보낸 값만.
 */
public record GoalExtraMetricRequest(
        @Size(max = 30, message = "그래프 이름은 30자 이하로 입력해 주세요.")
        String name,

        @Size(max = 10, message = "단위는 10자 이하로 입력해 주세요.")
        String unit,

        @DecimalMin(value = "-99999999.99") @DecimalMax(value = "99999999.99")
        BigDecimal startValue,

        @DecimalMin(value = "-99999999.99") @DecimalMax(value = "99999999.99")
        BigDecimal targetValue
) {
}
