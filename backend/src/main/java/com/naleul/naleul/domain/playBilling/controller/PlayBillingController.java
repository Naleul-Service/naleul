package com.naleul.naleul.domain.playBilling.controller;

import com.naleul.naleul.domain.playBilling.dto.request.PlayVerifyRequest;
import com.naleul.naleul.domain.playBilling.dto.response.PlayBillingStatusResponse;
import com.naleul.naleul.domain.playBilling.service.PlayBillingService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Android Google Play 구독
 *  GET  /api/v1/play-billing          Pro 여부 · 지금 Play 구독
 *  POST /api/v1/play-billing/verify   결제 직후 · 복원 · 화면 진입 때 구매 토큰 확인 → Pro
 *  POST /api/v1/play-billing/rtdn     Play 실시간 알림 (PlayRtdnController, 로그인 없음)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/play-billing")
public class PlayBillingController {

    private final PlayBillingService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PlayBillingStatusResponse>> status(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.PLAY_BILLING_FOUND, service.status(userId)));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PlayBillingStatusResponse>> verify(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody PlayVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.PLAY_BILLING_VERIFIED, service.verify(userId, request)));
    }
}
