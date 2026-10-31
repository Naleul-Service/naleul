package com.naleul.naleul.domain.chart.controller;

import com.naleul.naleul.domain.chart.dto.WeeklyReportDto;
import com.naleul.naleul.domain.chart.dto.WeeklyReportSummary;
import com.naleul.naleul.domain.chart.service.WeeklyReportOnDemandService;
import com.naleul.naleul.domain.chart.service.WeeklyReportQueryService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/weekly")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportQueryService weeklyReportQueryService;
    private final WeeklyReportOnDemandService weeklyReportOnDemandService;

    // 리스트: "26.06.1주차, 2주차, 3주차..." 형태로 보여줄 목록
    @GetMapping
    public ResponseEntity<ApiResponse<List<WeeklyReportSummary>>> getReportList(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND, weeklyReportQueryService.getReportSummaries(userId))
        );
    }

    // 상세: "확인하기" 눌렀을 때
    @GetMapping("/{weekStartDate}")
    public ResponseEntity<ApiResponse<WeeklyReportDto>> getReportDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate weekStartDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND, weeklyReportQueryService.getReportDetail(userId, weekStartDate))
        );
    }

    // ✅ 신규: "내 패턴 분석하기" 버튼 — 요청 시점에 맞춰 즉석 생성
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<WeeklyReportDto>> generateOnDemand(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND, weeklyReportOnDemandService.generateOnDemand(userId))
        );
    }
}