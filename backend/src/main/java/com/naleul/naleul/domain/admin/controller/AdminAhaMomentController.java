package com.naleul.naleul.domain.admin.controller;

import com.naleul.naleul.domain.admin.dto.AhaMomentResponse;
import com.naleul.naleul.domain.admin.service.AhaMomentService;
import com.naleul.naleul.domain.admin.support.AdminGuard;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 운영 대시보드 · Aha-moment (운영진 전용 — AdminGuard, V69).
 * GET /api/v1/admin/dashboard/aha?days=30  (기간 = 가입일 기준)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dashboard/aha")
public class AdminAhaMomentController {

    private final AdminGuard adminGuard;
    private final AhaMomentService service;

    @GetMapping
    public ResponseEntity<ApiResponse<AhaMomentResponse>> summary(@AuthenticationPrincipal Long userId,
                                                                  @RequestParam(defaultValue = "30") int days) {
        adminGuard.check(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ADMIN_DASHBOARD_FOUND, service.summary(days)));
    }
}
