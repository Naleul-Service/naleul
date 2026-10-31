package com.naleul.naleul.domain.admin.controller;

import com.naleul.naleul.domain.admin.dto.AdminViewPlanRequest;
import com.naleul.naleul.domain.admin.dto.AdminViewPlanResponse;
import com.naleul.naleul.domain.admin.service.AdminViewPlanService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 운영진 플랜 바꿔 보기 (운영진 전용 — AdminGuard).
 *
 * GET   /api/v1/admin/view-plan              지금 보고 있는 플랜 (기본 PRO)
 * PATCH /api/v1/admin/view-plan {plan:FREE}  Free 로 바꿔 보기
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/view-plan")
public class AdminViewPlanController {

    private final AdminViewPlanService service;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminViewPlanResponse>> get(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ADMIN_VIEW_PLAN_FOUND, service.get(userId)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<AdminViewPlanResponse>> change(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AdminViewPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ADMIN_VIEW_PLAN_CHANGED,
                service.change(userId, request.plan())));
    }
}
