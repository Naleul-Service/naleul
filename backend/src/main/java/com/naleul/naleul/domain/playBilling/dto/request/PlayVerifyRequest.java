package com.naleul.naleul.domain.playBilling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 앱의 Play 결제 결과 (Purchase.purchaseToken · products[0]) */
public record PlayVerifyRequest(
        @NotBlank @Size(max = 100) String productId,
        @NotBlank @Size(max = 512) String purchaseToken
) {
}
