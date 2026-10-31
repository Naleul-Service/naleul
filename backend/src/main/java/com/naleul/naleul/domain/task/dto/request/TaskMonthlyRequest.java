// TaskMonthlyRequest.java
package com.naleul.naleul.domain.task.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TaskMonthlyRequest(

        @NotNull(message = "연도는 필수입니다.")
        Integer year,

        @NotNull(message = "월은 필수입니다.")
        @Min(value = 1, message = "월은 1 이상이어야 합니다.")
        @Max(value = 12, message = "월은 12 이하이어야 합니다.")
        Integer month,

        String sourceType   // ✅ 추가
) {}