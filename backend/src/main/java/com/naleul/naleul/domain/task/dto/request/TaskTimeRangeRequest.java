package com.naleul.naleul.domain.task.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public record TaskTimeRangeRequest(

        @NotNull(message = "날짜는 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,          // 예: 2024-01-15 (어느 날짜의 시간대인지)

        @NotNull(message = "시작 시간은 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime startTime,     // 예: 13:00

        @NotNull(message = "종료 시간은 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime endTime        // 예: 14:00
) {}