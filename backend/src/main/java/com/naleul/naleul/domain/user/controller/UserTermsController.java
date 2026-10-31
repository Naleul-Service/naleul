package com.naleul.naleul.domain.user.controller;

import com.naleul.naleul.domain.user.dto.request.TermsAgreementRequest;
import com.naleul.naleul.domain.user.dto.response.TermsAgreementResponse;
import com.naleul.naleul.domain.user.service.UserTermsService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/terms-agreement")
public class UserTermsController {

    private final UserTermsService userTermsService;

    // 약관 동의 처리 (온보딩 1단계)
    @PostMapping
    public ResponseEntity<ApiResponse<TermsAgreementResponse>> agreeToTerms(
            @RequestBody TermsAgreementRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        TermsAgreementResponse response = userTermsService.agreeToTerms(userId, request);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TERMS_AGREED, response));
    }

    // UserTermsController에 추가
    @PatchMapping("/onboarding-complete")
    public ResponseEntity<ApiResponse<Void>> completeOnboarding(
            @AuthenticationPrincipal Long userId
    ) {
        userTermsService.completeOnboarding(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.OK, null));
    }
}