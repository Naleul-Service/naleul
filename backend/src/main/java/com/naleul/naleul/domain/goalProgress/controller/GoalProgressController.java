package com.naleul.naleul.domain.goalProgress.controller;

import com.naleul.naleul.domain.goalProgress.dto.request.GoalCheckInRequest;
import com.naleul.naleul.domain.goalProgress.dto.request.GoalMetricLogRequest;
import com.naleul.naleul.domain.goalProgress.dto.response.GoalProgressResponse;
import com.naleul.naleul.domain.goalProgress.service.GoalProgressService;
import com.naleul.naleul.domain.goalProgress.service.GoalAdjustmentService;
import com.naleul.naleul.domain.goalOutcome.service.GoalOutcomeRecorder;
import com.naleul.naleul.domain.goalProgress.dto.request.GoalAdjustmentRequest;
import com.naleul.naleul.domain.goalProgress.dto.request.GoalOutcomeRequest;
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
 * 목표 상세 "지금 어디쯤?" 도표.
 * GET    /goal-categories/{id}/progress            진행 데이터
 * PUT    /goal-categories/{id}/metric-logs         오늘(또는 지정 날짜) 수치 기록 — 같은 날짜는 덮어씀
 * DELETE /goal-categories/{id}/metric-logs/{date}  그날 기록 지우기
 * PUT    /goal-categories/{id}/check-in            오늘 기록 + 회고 (수치 · 무엇을 했는지 · 어땠는지)
 * 기록 · 삭제 응답도 진행 데이터 전체라서 화면은 받은 값으로 바로 다시 그린다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goal-categories/{goalCategoryId}")
public class GoalProgressController {

    private final GoalProgressService goalProgressService;
    private final GoalAdjustmentService adjustmentService;
    private final GoalOutcomeRecorder outcomeRecorder;

    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> getProgress(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_PROGRESS_FOUND,
                goalProgressService.getProgress(userId, goalCategoryId)));
    }

    @PutMapping("/metric-logs")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> record(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalMetricLogRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_METRIC_LOGGED,
                goalProgressService.record(userId, goalCategoryId, request)));
    }

    @PutMapping("/check-in")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> checkIn(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalCheckInRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_METRIC_LOGGED,
                goalProgressService.checkIn(userId, goalCategoryId, request)));
    }

    @DeleteMapping("/metric-logs/{date}")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> delete(
            @PathVariable Long goalCategoryId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_METRIC_LOG_DELETED,
                goalProgressService.delete(userId, goalCategoryId, date)));
    }

    /** 주간 점검 제안 반영 — 루틴 요일을 내일부터 줄이거나 늘린다 */
    @PostMapping("/adjustments/apply")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> applyAdjustment(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalAdjustmentRequest request
    ) {
        adjustmentService.apply(userId, goalCategoryId, request.key());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_ADJUSTMENT_APPLIED,
                goalProgressService.getProgress(userId, goalCategoryId)));
    }

    /** 주간 점검 제안 미루기 — 7일 동안 같은 제안을 다시 내지 않는다 */
    @PostMapping("/adjustments/dismiss")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> dismissAdjustment(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalAdjustmentRequest request
    ) {
        adjustmentService.dismiss(userId, goalCategoryId, request.key());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_ADJUSTMENT_DISMISSED,
                goalProgressService.getProgress(userId, goalCategoryId)));
    }

    /** 끝난 목표의 결과 다시 고르기 (자동 판정을 고치거나, 결과가 없던 옛 목표) */
    @PatchMapping("/outcome")
    public ResponseEntity<ApiResponse<GoalProgressResponse>> changeOutcome(
            @PathVariable Long goalCategoryId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoalOutcomeRequest request
    ) {
        outcomeRecorder.changeResult(userId, goalCategoryId, request.result());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.GOAL_OUTCOME_UPDATED,
                goalProgressService.getProgress(userId, goalCategoryId)));
    }
}
