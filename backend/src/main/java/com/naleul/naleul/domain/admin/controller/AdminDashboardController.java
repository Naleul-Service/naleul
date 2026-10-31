package com.naleul.naleul.domain.admin.controller;

import com.naleul.naleul.domain.admin.dto.AdminDashboardResponse;
import com.naleul.naleul.domain.admin.service.AdminDashboardService;
import com.naleul.naleul.domain.admin.support.AdminGuard;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 대시보드 (운영진 전용 — AdminGuard).
 *
 * GET /api/v1/admin/dashboard            최근 30일
 * GET /api/v1/admin/dashboard?days=7     최근 7일 (1 ~ 3650)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AdminGuard adminGuard;
    private final AdminDashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> get(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "30") int days) {
        adminGuard.check(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ADMIN_DASHBOARD_FOUND, dashboardService.get(days)));
    }
}
