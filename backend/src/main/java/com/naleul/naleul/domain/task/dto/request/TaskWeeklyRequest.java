// TaskWeeklyRequest.java
package com.naleul.naleul.domain.task.dto.request;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record TaskWeeklyRequest(

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,

        Long goalCategoryId,
        Long generalCategoryId,
        String priority,
        String sourceType   // ✅ 추가
) {}