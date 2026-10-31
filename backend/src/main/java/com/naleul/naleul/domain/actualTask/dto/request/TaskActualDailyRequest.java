package com.naleul.naleul.domain.actualTask.dto.request;

import java.time.LocalDate;

public record TaskActualDailyRequest(
        LocalDate date,
        Long goalCategoryId,      // nullable
        Long generalCategoryId,   // nullable
        String sourceType         // nullable: "MANUAL" | "ROUTINE" | "MISSION"
) {}