package com.naleul.naleul.domain.purchase.controller;

import com.naleul.naleul.domain.purchase.dto.request.VerifyPurchaseRequest;
import com.naleul.naleul.domain.purchase.service.IapService;
import com.naleul.naleul.global.common.response.ApiResponse;
import com.naleul.naleul.global.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iap")
public class IapController {

    private final IapService iapService;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyPurchase(
            @Valid @RequestBody VerifyPurchaseRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        iapService.verifyAndSavePurchase(userId, request.getSignedTransaction());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.PURCHASE_VERIFIED, null));
    }
}