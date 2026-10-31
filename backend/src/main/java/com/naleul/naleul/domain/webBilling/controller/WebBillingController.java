package com.naleul.naleul.domain.webBilling.controller;

import com.naleul.naleul.domain.webBilling.dto.request.TossPayPrepareRequest;
import com.naleul.naleul.domain.webBilling.dto.request.WebBillingConfirmRequest;
import com.naleul.naleul.domain.webBilling.dto.request.WebBillingPrepareRequest;
import com.naleul.naleul.domain.webBilling.dto.response.TossPayPrepareResponse;
import com.naleul.naleul.domain.webBilling.dto.response.WebBillingPrepareResponse;
import com.naleul.naleul.domain.webBilling.dto.response.WebBillingStatusResponse;
import com.naleul.naleul.domain.webBilling.service.WebBillingService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 웹 Pro 정기구독 (카드 · 토스페이)
 *  GET  /api/v1/web-billing          구독 상태 · 상품 · 결제 내역
 *  [카드 정기구독 · 토스페이먼츠]
 *  POST /api/v1/web-billing/prepare  카드 등록 창 열기 전 (구매자 키)
 *  POST /api/v1/web-billing/confirm  카드 등록 성공 → 첫 결제 · Pro
 *  [토스페이 정기구독]
 *  POST /api/v1/web-billing/toss-pay/prepare  토스 앱 인증 주소
 *  POST /api/v1/web-billing/toss-pay/confirm  인증 후 돌아옴 → 첫 결제 · Pro
 *  POST /api/v1/web-billing/toss-pay/callback 토스 → 서버 (WebBillingTossPayCallbackController, 로그인 없음)
 *  [공통]
 *  POST /api/v1/web-billing/cancel   해지 (기간 끝까지 이용)
 *  POST /api/v1/web-billing/resume   해지 취소
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/web-billing")
public class WebBillingController {

    private final WebBillingService billingService;

    @GetMapping
    public ResponseEntity<ApiResponse<WebBillingStatusResponse>> status(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WEB_BILLING_FOUND, billingService.status(userId)));
    }

    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<WebBillingPrepareResponse>> prepare(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody WebBillingPrepareRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WEB_BILLING_PREPARED,
                billingService.prepare(userId, request.plan())));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<WebBillingStatusResponse>> confirm(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody WebBillingConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WEB_BILLING_SUBSCRIBED,
                billingService.confirm(userId, request)));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<WebBillingStatusResponse>> cancel(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WEB_BILLING_CANCELED, billingService.cancel(userId)));
    }

    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<WebBillingStatusResponse>> resume(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WEB_BILLING_RESUMED, billingService.resume(userId)));
    }

    @PostMapping("/toss-pay/prepare")
    public ResponseEntity<ApiResponse<TossPayPrepareResponse>> prepareTossPay(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody TossPayPrepareRequest request) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WEB_BILLING_TOSS_PAY_PREPARED,
                billingService.prepareTossPay(userId, request)));
    }

    @PostMapping("/toss-pay/confirm")
    public ResponseEntity<ApiResponse<WebBillingStatusResponse>> confirmTossPay(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.WEB_BILLING_SUBSCRIBED,
                billingService.confirmTossPay(userId)));
    }
}
