package com.naleul.naleul.domain.chart.controller;

import com.naleul.naleul.domain.chart.dto.GoalCompletionReportDto;
import com.naleul.naleul.domain.chart.service.GoalCompletionReportQueryService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goal-categories/{goalCategoryId}/completion-report")
@RequiredArgsConstructor
public class GoalCompletionReportController {

    private final GoalCompletionReportQueryService goalCompletionReportQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<GoalCompletionReportDto>> getReport(
            @PathVariable Long goalCategoryId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(SuccessCode.CHART_FOUND, goalCompletionReportQueryService.getReport(goalCategoryId))
        );
    }
}