package com.naleul.naleul.domain.admin.controller;

import com.naleul.naleul.domain.admin.dto.ChatReviewItemsResponse;
import com.naleul.naleul.domain.admin.dto.ChatReviewResponse;
import com.naleul.naleul.domain.admin.service.ChatReviewService;
import com.naleul.naleul.domain.admin.support.AdminGuard;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 운영 대시보드 · AI 채팅 정리 정확도 (운영진 전용 — AdminGuard, V67).
 *
 * GET /api/v1/admin/dashboard/chat-review?days=30&goalType=HEALTH&promptVersion=2026-10-25&answerSource=CHIP
 * GET /api/v1/admin/dashboard/chat-review/items?field=PRACTICE_PREFERENCE&result=CHANGED&page=0 (+ 같은 필터)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dashboard/chat-review")
public class AdminChatReviewController {

    private final AdminGuard adminGuard;
    private final ChatReviewService service;

    @GetMapping
    public ResponseEntity<ApiResponse<ChatReviewResponse>> summary(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String goalType,
            @RequestParam(required = false) String promptVersion,
            @RequestParam(required = false) String answerSource) {
        adminGuard.check(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ADMIN_DASHBOARD_FOUND,
                service.summary(days, goalType, promptVersion, answerSource)));
    }

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<ChatReviewItemsResponse>> items(
            @AuthenticationPrincipal Long userId,
            @RequestParam String field,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "CHANGED") String result,
            @RequestParam(required = false) String goalType,
            @RequestParam(required = false) String promptVersion,
            @RequestParam(required = false) String answerSource,
            @RequestParam(defaultValue = "0") int page) {
        adminGuard.check(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.ADMIN_DASHBOARD_FOUND,
                service.items(field, days, result, goalType, promptVersion, answerSource, page)));
    }
}
