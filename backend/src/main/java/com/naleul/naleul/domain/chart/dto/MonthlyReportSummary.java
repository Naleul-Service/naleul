package com.naleul.naleul.domain.chart.dto;


import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MonthlyReportSummary {
    private Long monthlyReportId;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private String label; // "26년 6월 패턴 분석"
}