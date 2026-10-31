package com.naleul.naleul.domain.chart.controller;

import com.naleul.naleul.domain.chart.dto.GoalRecommendationDto;
import com.naleul.naleul.domain.chart.dto.MonthlyReportDto;
import com.naleul.naleul.domain.chart.dto.MonthlyReportSummary;
import com.naleul.naleul.domain.chart.service.MonthlyReportOnDemandService;
import com.naleul.naleul.domain.chart.service.MonthlyReportQueryService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/monthly")
@RequiredArgsConstructor
public class MonthlyReportController {

    private final MonthlyReportQueryService monthlyReportQueryService;
    private final MonthlyReportOnDemandService monthlyReportOnDemandService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MonthlyReportSummary>>> getReportList(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND, monthlyReportQueryService.getReportSummaries(userId))
        );
    }

    @GetMapping("/{periodStartDate}")
    public ResponseEntity<ApiResponse<MonthlyReportDto>> getReportDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStartDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND, monthlyReportQueryService.getReportDetail(userId, periodStartDate))
        );
    }

    // ✅ "월간 패턴 분석하기" 버튼용
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<MonthlyReportDto>> generateOnDemand(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND, monthlyReportOnDemandService.generateOnDemand(userId))
        );
    }

    // ✅ "진단 기반 목표 수정하기" 버튼용
    @GetMapping("/{periodStartDate}/recommendation")
    public ResponseEntity<ApiResponse<GoalRecommendationDto>> getGoalRecommendation(
            @AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStartDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND, monthlyReportQueryService.getGoalRecommendation(userId, periodStartDate))
        );
    }
}