// TaskActualWeeklyRequest.java
package com.naleul.naleul.domain.actualTask.dto.request;

import java.time.LocalDate;

public record TaskActualWeeklyRequest(
        LocalDate startDate,
        LocalDate endDate,
        Long goalCategoryId,
        Long generalCategoryId,
        String sourceType   // ✅ 추가
) {}