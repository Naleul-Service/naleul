package com.naleul.naleul.domain.aiUsage.controller;

import com.naleul.naleul.domain.aiUsage.dto.AiUsageResponse;
import com.naleul.naleul.domain.aiUsage.service.AiUsageService;
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
 * GET /api/v1/ai-usage                  오늘 사용량 (AI 목표 만들기 · AI 계획 초안 · Task 추가)
 * GET /api/v1/ai-usage?sessionId=12     + 그 목표 대화에서 계획을 다시 만들 수 있는 횟수
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai-usage")
public class AiUsageController {

    private final AiUsageService aiUsageService;

    @GetMapping
    public ResponseEntity<ApiResponse<AiUsageResponse>> get(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.AI_USAGE_FOUND, aiUsageService.get(userId, sessionId)));
    }
}
