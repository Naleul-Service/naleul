package com.naleul.naleul.domain.planner.controller;

import com.naleul.naleul.domain.planner.dto.request.FillApplyRequest;
import com.naleul.naleul.domain.planner.dto.request.FillPreviewTasksRequest;
import com.naleul.naleul.domain.planner.dto.response.DailyPlanResponse;
import com.naleul.naleul.domain.planner.dto.response.FillApplyResponse;
import com.naleul.naleul.domain.planner.dto.response.FillPreviewResponse;
import com.naleul.naleul.domain.planner.dto.response.FillResponse;
import com.naleul.naleul.domain.planner.dto.response.ReplanResponse;
import com.naleul.naleul.domain.planner.service.DailyPlanService;
import com.naleul.naleul.domain.planner.service.FillProposalService;
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
 * GET  /api/v1/daily-plans/{date}           생성 기록 (배너용: needsReview)
 * POST /api/v1/daily-plans/{date}/review    "이대로 진행하기"
 * POST /api/v1/daily-plans/{date}/replan    "다시 배치" (지난 날짜는 400)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/daily-plans")
public class DailyPlanController {

    private final DailyPlanService dailyPlanService;
    private final FillProposalService fillProposalService;

    @GetMapping("/{date}")
    public ResponseEntity<ApiResponse<DailyPlanResponse>> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DAILY_PLAN_FOUND, dailyPlanService.get(userId, date)));
    }

    @PostMapping("/{date}/review")
    public ResponseEntity<ApiResponse<DailyPlanResponse>> review(
            @AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DAILY_PLAN_REVIEWED, dailyPlanService.review(userId, date)));
    }

    /**
     * "AI로 TimeTable 배치하기" — 기간의 시간 미정 Task 만 빈 시간에 채운다 (이미 정해진 블록은 그대로).
     * POST /api/v1/daily-plans/fill?startDate=2026-10-06&endDate=2026-10-12 (최대 7일, 지난 날짜는 건너뜀)
     */
    @PostMapping("/fill")
    public ResponseEntity<ApiResponse<FillResponse>> fill(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DAILY_PLAN_FILLED,
                dailyPlanService.fill(userId, startDate, endDate)));
    }

    /**
     * "AI로 TimeTable 배치하기" 미리보기 — 저장하지 않고 어디에 놓일지만 (화면은 점선으로 보여줌)
     * POST /api/v1/daily-plans/fill/preview?startDate=…&endDate=…
     */
    @PostMapping("/fill/preview")
    public ResponseEntity<ApiResponse<FillPreviewResponse>> previewFill(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DAILY_PLAN_FILLED,
                fillProposalService.preview(userId, startDate, endDate)));
    }

    /**
     * 정해진 Task 들만 배치 미리보기 — Brain dump 로 만든 Task 를 바로 놓지 않고 점선으로 먼저 보여줄 때
     * POST /api/v1/daily-plans/fill/preview-tasks  { "taskIds": [1, 2, 3] }
     */
    @PostMapping("/fill/preview-tasks")
    public ResponseEntity<ApiResponse<FillPreviewResponse>> previewFillTasks(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FillPreviewTasksRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DAILY_PLAN_FILLED,
                fillProposalService.previewTasks(userId, request.taskIds())));
    }

    /** 미리보기에서 고친 최종 자리로 확정 ("이대로 진행할까요?" → 예) */
    @PostMapping("/fill/apply")
    public ResponseEntity<ApiResponse<FillApplyResponse>> applyFill(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FillApplyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DAILY_PLAN_FILLED,
                fillProposalService.apply(userId, request)));
    }

    @PostMapping("/{date}/replan")
    public ResponseEntity<ApiResponse<ReplanResponse>> replan(
            @AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.DAILY_PLAN_REPLANNED, dailyPlanService.replan(userId, date)));
    }
}
