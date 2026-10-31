package com.naleul.naleul.domain.webBilling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 토스 카드 등록 성공 페이지(successUrl)로 받은 값 그대로 */
public record WebBillingConfirmRequest(
        @NotBlank @Size(max = 300) String authKey,
        @NotBlank @Size(max = 64) String customerKey
) {
}
