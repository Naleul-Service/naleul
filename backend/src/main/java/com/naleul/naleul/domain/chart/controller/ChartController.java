package com.naleul.naleul.domain.chart.controller;

import com.naleul.naleul.domain.chart.dto.*;
import com.naleul.naleul.domain.chart.service.ChartService;
import com.naleul.naleul.domain.chart.service.GoalCompletionReportQueryService;
import com.naleul.naleul.domain.goalCategory.enums.GoalCategoryStatus;
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
@RequestMapping("/api/v1/charts")
@RequiredArgsConstructor
public class ChartController {

    private final ChartService chartService;
    private final GoalCompletionReportQueryService goalCompletionReportQueryService;

    @GetMapping("/goal-categories")
    public ResponseEntity<ApiResponse<ChartResponseDto>> getGoalCategoryChart(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND,
                        chartService.getGoalCategoryChart(userId, startDate, endDate))
        );
    }

    @GetMapping("/goal-categories/detail")
    public ResponseEntity<ApiResponse<List<GoalCategoryChartDto>>> getGoalDetailChart(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND,
                        chartService.getGoalDetailChart(userId, startDate, endDate))
        );
    }

    @GetMapping("/general-categories")
    public ResponseEntity<ApiResponse<ChartResponseDto>> getGeneralCategoryChart(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND,
                        chartService.getGeneralCategoryChart(userId, startDate, endDate))
        );
    }

    @GetMapping("/achievement")
    public ResponseEntity<ApiResponse<AchievementChartDto>> getPlanAchievementChart(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND,
                        chartService.getPlanAchievementChart(userId, startDate, endDate))
        );
    }

    @GetMapping("/achievement/routine")
    public ResponseEntity<ApiResponse<AchievementChartDto>> getRoutineAchievementChart(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND,
                        chartService.getRoutineAchievementChart(userId, startDate, endDate))
        );
    }

    @GetMapping("/goal-categories/statistics")
    public ResponseEntity<ApiResponse<List<GoalCategoryStatisticsDto>>> getGoalCategoryStatistics(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) GoalCategoryStatus status
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND,
                        chartService.getGoalCategoryStatistics(userId, status))
        );
    }

    // ✅ 목표 상세 - "시간" 탭 (도넛차트 + 막대차트)
    @GetMapping("/goal-categories/{goalCategoryId}/time")
    public ResponseEntity<ApiResponse<GoalCategoryTimeStatisticsDto>> getGoalCategoryTimeStatistics(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND,
                        chartService.getGoalCategoryTimeStatistics(userId, goalCategoryId, startDate, endDate))
        );
    }

    // ✅ 목표 상세 - "실천률" 탭 (달성률 + 연속달성 + 히트맵)
    @GetMapping("/goal-categories/{goalCategoryId}/achievement")
    public ResponseEntity<ApiResponse<GoalCategoryAchievementStatisticsDto>> getGoalCategoryAchievementStatistics(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND,
                        chartService.getGoalCategoryAchievementStatistics(userId, goalCategoryId, startDate, endDate))
        );
    }

    // ✅ 목표 상세 - "패턴분석" 탭 (시간/요일/기간 패턴, 완료된 목표만 데이터 채워짐)
    @GetMapping("/goal-categories/{goalCategoryId}/pattern")
    public ResponseEntity<ApiResponse<GoalPatternDetailDto>> getGoalCategoryPatternStatistics(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long goalCategoryId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND,
                        goalCompletionReportQueryService.getPatternDetail(goalCategoryId))
        );
    }
}