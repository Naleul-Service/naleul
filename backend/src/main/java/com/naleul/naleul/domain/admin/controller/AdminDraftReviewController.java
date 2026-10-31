package com.naleul.naleul.domain.admin.controller;

import com.naleul.naleul.domain.admin.dto.DraftReviewItemsResponse;
import com.naleul.naleul.domain.admin.dto.DraftReviewResponse;
import com.naleul.naleul.domain.admin.service.DraftReviewService;
import com.naleul.naleul.domain.admin.support.AdminGuard;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 운영 대시보드 · AI 초안 정확도 (운영진 전용 — AdminGuard, V68).
 *
 * GET /api/v1/admin/dashboard/draft-review?days=30&window=14&goalType=HEALTH&intensity=STEADY&planningStyle=PLANNER&promptVersion=2026-10-25
 * GET /api/v1/admin/dashboard/draft-review/items?kind=ROUTINE&result=ALL&page=0 (+ 같은 필터)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dashboard/draft-review")
public class AdminDraftReviewController {

    private final AdminGuard adminGuard;
    private final DraftReviewService service;

    @GetMapping
    public ResponseEntity<ApiResponse<DraftReviewResponse>> summary(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "14") int window,
            @RequestParam(required = false) String goalType,
            @RequestParam(required = false) String intensity,
            @RequestParam(required = false) String planningStyle,
            @RequestParam(required = false) String promptVersion) {
        adminGuard.check(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ADMIN_DASHBOARD_FOUND,
                service.summary(days, goalType, intensity, planningStyle, promptVersion, window)));
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<DraftReviewItemsResponse>> items(
            @AuthenticationPrincipal Long userId,
            @RequestParam String kind,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "14") int window,
            @RequestParam(defaultValue = "ALL") String result,
            @RequestParam(required = false) String goalType,
            @RequestParam(required = false) String intensity,
            @RequestParam(required = false) String planningStyle,
            @RequestParam(required = false) String promptVersion,
            @RequestParam(defaultValue = "0") int page) {
        adminGuard.check(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ADMIN_DASHBOARD_FOUND,
                service.items(kind, days, result, goalType, intensity, planningStyle, promptVersion, window, page)));
    }
}
