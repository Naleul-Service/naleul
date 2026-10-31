package com.naleul.naleul.domain.task.dto.request;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record TaskDailyRequest(

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,

        Long goalCategoryId,
        Long generalCategoryId,
        String priority,
        String sourceType   // ✅ 추가: "MANUAL" | "ROUTINE" | "MISSION"
) {}