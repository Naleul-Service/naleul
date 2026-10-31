package com.naleul.naleul.domain.chart.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class WeeklyReportSummary {
    private Long weeklyReportId;
    private LocalDate weekStartDate;   // 상세 조회 시 경로 파라미터로 사용
    private LocalDate weekEndDate;
    private int year;
    private int month;
    private int weekOfMonth;
    private String label;              // "26. 06. 1주차"
}