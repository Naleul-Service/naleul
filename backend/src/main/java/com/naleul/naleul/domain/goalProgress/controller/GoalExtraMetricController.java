package com.naleul.naleul.domain.goalProgress.controller;

import com.naleul.naleul.domain.goalProgress.dto.request.GoalExtraMetricRequest;
import com.naleul.naleul.domain.goalProgress.dto.request.GoalMetricLogRequest;
import com.naleul.naleul.domain.goalProgress.dto.response.GoalProgressResponse;
import com.naleul.naleul.domain.goalProgress.service.GoalExtraMetricService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 목표형 추가 그래프 (V52) — 메인 그래프(수치)는 기존 /metric-logs 그대로
 * POST   /goal-categories/{id}/metrics                      그래프 추가 (최대 5개)
 * PATCH  /goal-categories/{id}/metrics/{metricId}           이름 · 단위 · 시작값 · 목표값 고치기
 * DELETE /goal-categories/{id}/metrics/{metricId}           그래프 지우기
 * PUT    /goal-categories/{id}/metrics/{metricId}/logs      기록 (date 없으면 오늘, 같은 날짜는 덮어씀)
 * DELETE /goal-categories/{id}/metrics/{metricId}/logs/{date}
 * 응답은 모두 GET /progress 와 같은 진행 데이터 (extraMetrics 포함)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goal-categories/{goalCategoryId}/metrics")
public class GoalExtraMetricController {

    private final GoalExtraMetricService service;

    @PostMapping
    public ResponseEntity<ApiResponse<GoalProgressResponse>> create(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalExtraMetricRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_EXTRA_METRIC_SAVED,
                service.create(userId, goalCategoryId, request)));
    }

    @PatchMapping("/{metricId}")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> update(
            @PathVariable Long goalCategoryId,
            @PathVariable Long metricId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalExtraMetricRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_EXTRA_METRIC_SAVED,
                service.update(userId, goalCategoryId, metricId, request)));
    }

    @DeleteMapping("/{metricId}")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> delete(
            @PathVariable Long goalCategoryId,
            @PathVariable Long metricId,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_EXTRA_METRIC_DELETED,
                service.delete(userId, goalCategoryId, metricId)));
    }

    @PutMapping("/{metricId}/logs")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> record(
            @PathVariable Long goalCategoryId,
            @PathVariable Long metricId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalMetricLogRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_METRIC_LOGGED,
                service.record(userId, goalCategoryId, metricId, request)));
    }

    @DeleteMapping("/{metricId}/logs/{date}")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> deleteLog(
            @PathVariable Long goalCategoryId,
            @PathVariable Long metricId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_METRIC_LOG_DELETED,
                service.deleteLog(userId, goalCategoryId, metricId, date)));
    }
}
