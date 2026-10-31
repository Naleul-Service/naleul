package com.naleul.naleul.domain.admin.controller;

import com.naleul.naleul.domain.admin.dto.AiInterestResponse;
import com.naleul.naleul.domain.admin.service.AiInterestService;
import com.naleul.naleul.domain.admin.support.AdminGuard;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 운영 대시보드 · AI 관심도 (운영진 전용 — AdminGuard, V70).
 * GET /api/v1/admin/dashboard/ai-interest?days=30  (기능별 시도 · 완료 · 중단, Free / Pro)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dashboard/ai-interest")
public class AdminAiInterestController {

    private final AdminGuard adminGuard;
    private final AiInterestService service;

    @GetMapping
    public ResponseEntity<ApiResponse<AiInterestResponse>> summary(@AuthenticationPrincipal Long userId,
                                                                  @RequestParam(defaultValue = "30") int days) {
        adminGuard.check(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ADMIN_DASHBOARD_FOUND, service.summary(days)));
    }
}
